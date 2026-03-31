package com.aieap.platform.document;

import com.aieap.platform.common.ai.LlmClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handles vector embedding and Qdrant indexing asynchronously so that the
 * document upload endpoint returns immediately (status = INDEXING).
 * On completion the document status is updated to READY; on failure to FAILED.
 */
@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final LlmClient llmClient;
    private final QdrantVectorStoreClient qdrantVectorStoreClient;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public DocumentIndexingService(LlmClient llmClient, QdrantVectorStoreClient qdrantVectorStoreClient) {
        this.llmClient = llmClient;
        this.qdrantVectorStoreClient = qdrantVectorStoreClient;
    }

    /**
     * Runs in a background thread. Generates embeddings for each chunk, stores
     * vectors in Qdrant, and writes the resolved vector_id back to PostgreSQL.
     * Updates the document processing_status to READY or FAILED when done.
     */
    @Async("documentIndexingExecutor")
    public void indexDocumentAsync(String documentId, List<String> chunkIds, List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            updateDocumentStatus(documentId, "READY");
            return;
        }

        if (!llmClient.isConfigured() || !qdrantVectorStoreClient.isAvailable()) {
            log.warn("[ASYNC-INDEX] LLM or Qdrant not available — marking document {} as INDEXED_NO_VECTOR", documentId);
            updateDocumentStatus(documentId, "INDEXED_NO_VECTOR");
            return;
        }

        log.info("[ASYNC-INDEX] Starting vector indexing for document={} chunks={}", documentId, chunks.size());
        boolean collectionReady = false;
        int indexed = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            String chunkId = chunkIds.get(i);
            try {
                List<Double> embedding = llmClient.embedding(content);
                if (embedding.isEmpty()) {
                    log.warn("[ASYNC-INDEX] Empty embedding for chunk {} of document {}", i, documentId);
                    continue;
                }

                if (!collectionReady) {
                    qdrantVectorStoreClient.ensureCollection(embedding.size());
                    collectionReady = true;
                }

                String pointId = documentId + "-" + i;
                qdrantVectorStoreClient.upsertPoint(
                    pointId,
                    embedding,
                    Map.of(
                        "documentId", documentId,
                        "chunkId", chunkId,
                        "chunkIndex", i
                    )
                );

                if (jdbcTemplate != null) {
                    jdbcTemplate.update(
                        "UPDATE aieap.document_chunks SET vector_id = ? WHERE id = ?::uuid",
                        pointId, chunkId
                    );
                }
                indexed++;
            } catch (Exception ex) {
                log.error("[ASYNC-INDEX] Failed to index chunk {} of document {}: {}", i, documentId, ex.getMessage(), ex);
            }
        }

        if (indexed > 0) {
            log.info("[ASYNC-INDEX] Indexed {}/{} chunks for document={}", indexed, chunks.size(), documentId);
            updateDocumentStatus(documentId, "READY");
        } else {
            log.warn("[ASYNC-INDEX] No chunks indexed for document={} — marking FAILED", documentId);
            updateDocumentStatus(documentId, "FAILED");
        }
    }

    private void updateDocumentStatus(String documentId, String status) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                "UPDATE aieap.documents SET processing_status = ?, updated_at = NOW() WHERE id = ?::uuid",
                status, documentId
            );
        } catch (Exception ex) {
            log.error("[ASYNC-INDEX] Failed to update document status to {} for id={}: {}", status, documentId, ex.getMessage());
        }
    }
}
