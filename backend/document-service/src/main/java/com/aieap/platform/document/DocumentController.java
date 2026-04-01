package com.aieap.platform.document;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.ai.LlmClient;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.NullOrValidUuid;
import com.aieap.platform.document.kafka.KafkaEventPublisher;
import com.aieap.platform.document.kafka.events.DocumentChunksReadyEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Documents")
@RequestMapping("/documents")
@PreAuthorize("isAuthenticated()")
public class DocumentController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentRagService documentRagService;
    private final LlmClient llmClient;
    private final QdrantVectorStoreClient qdrantVectorStoreClient;
    private final DocumentIndexingService documentIndexingService;

    public DocumentController(
        DocumentRagService documentRagService,
        LlmClient llmClient,
        QdrantVectorStoreClient qdrantVectorStoreClient,
        DocumentIndexingService documentIndexingService
    ) {
        this.documentRagService = documentRagService;
        this.llmClient = llmClient;
        this.qdrantVectorStoreClient = qdrantVectorStoreClient;
        this.documentIndexingService = documentIndexingService;
    }

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @CacheEvict(value = "documents", allEntries = true)
    public ApiEnvelope<DocumentItem> upload(
        @RequestPart("file") MultipartFile file,
        @RequestPart(value = "ownerUserId", required = false) @NullOrValidUuid String ownerUserId,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        validateUpload(file);
        String id = UUID.randomUUID().toString();
        String fileName = InputSanitizer.fileName(file.getOriginalFilename());
        String fileType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String storagePath = "local://uploads/" + id + "/" + fileName;
        DocumentRagService.ProcessedDocument processed = documentRagService.processUpload(file);

        db.update(
            "INSERT INTO aieap.documents (id, owner_user_id, file_name, file_type, file_size, storage_path, processing_status, summary, created_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            id,
            parseUuidOrNull(ownerUserId),
            fileName,
            fileType,
            file.getSize(),
            storagePath,
            "UPLOADED",
            processed.summary()
        );

        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < processed.chunks().size(); i++) {
            String content = processed.chunks().get(i);
            String chunkId = UUID.randomUUID().toString();
            chunkIds.add(chunkId);
            String citationLabel = generateCitationLabel(fileName, i, 0);
            db.update(
                "INSERT INTO aieap.document_chunks (id, document_id, chunk_index, content, token_count, vector_id, citation_label) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)",
                chunkId,
                id,
                i,
                content,
                estimateTokenCount(content),
                null,
                citationLabel
            );
        }

        // Schedule async vector indexing — upload returns immediately with status INDEXING.
        // DocumentIndexingService will update status to READY (or FAILED) when done.
        if (!processed.chunks().isEmpty()) {
            db.update(
                "UPDATE aieap.documents SET processing_status = ?, updated_at = NOW() WHERE id = ?::uuid",
                "INDEXING",
                id
            );
            documentIndexingService.indexDocumentAsync(id, chunkIds, processed.chunks());
        }

        DocumentItem document = document(id);
        if (kafkaEventPublisher != null) {
            String correlationId = request.getHeader("X-Correlation-ID") != null
                ? request.getHeader("X-Correlation-ID")
                : java.util.UUID.randomUUID().toString();
            kafkaEventPublisher.publish(
                "document.chunks.ready",
                id,
                new DocumentChunksReadyEvent(
                    java.util.UUID.randomUUID().toString(),
                    correlationId,
                    id,
                    fileName,
                    chunkIds.size(),
                    processed.chunks().isEmpty() ? "UPLOADED" : "RAG_READY",
                    ownerUserId,
                    java.time.OffsetDateTime.now().toString()
                ),
                correlationId
            );
        }
        return ResponseFactory.success(request, document);
    }

    @GetMapping
    @Cacheable(value = "documents", key = "'list:p=' + #page + ':s=' + #size")
    public ApiEnvelope<PageEnvelope<DocumentItem>> list(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        List<DocumentItem> items = loadDocuments();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        List<DocumentItem> pageItems = new ArrayList<>(items.subList(fromIndex, toIndex));
        return ResponseFactory.success(request, new PageEnvelope<>(pageItems, page, size, items.size(), "createdAt,DESC"));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<DocumentItem> get(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseFactory.success(request, document(id.toString()));
    }

    @PostMapping("/{id}/ask")
    @Cacheable(value = "doc-answers", key = "#id + ':' + #askDocumentRequest.question().toLowerCase().strip()")
    public ApiEnvelope<DocumentAnswer> ask(@PathVariable UUID id, @Valid @RequestBody AskDocumentRequest askDocumentRequest, HttpServletRequest request) {
        String documentId = id.toString();
        String question = InputSanitizer.requiredText(askDocumentRequest.question());
        document(documentId);
        RetrievalResult retrievalResult = loadRelevantChunks(documentId, question, 8);
        List<DocumentChunk> citations = retrievalResult.chunks();
        String answer = documentRagService.answerQuestion(question, citations);

        double confidence;
        if (citations.isEmpty()) {
            confidence = 0.2;
        } else if (retrievalResult.mode() == RetrievalMode.VECTOR) {
            confidence = 0.9;
        } else {
            confidence = 0.75;
        }

        return ResponseFactory.success(request, new DocumentAnswer(
            question,
            answer,
            confidence,
            new ArrayList<>(citations)
        ));
    }

    @GetMapping("/{id}/chunks")
    public ApiEnvelope<List<DocumentChunk>> getChunks(@PathVariable UUID id, HttpServletRequest request) {
        String documentId = id.toString();
        document(documentId);
        return ResponseFactory.success(request, loadChunks(documentId));
    }

    private DocumentItem document(String id) {
        JdbcTemplate db = requireJdbc();
        List<DocumentItem> rows = db.query(
            "SELECT id::text AS id, file_name, file_type, file_size, processing_status, summary, created_at FROM aieap.documents WHERE id = ?::uuid",
            (rs, rowNum) -> new DocumentItem(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("processing_status"),
                rs.getString("summary"),
                readOffsetDateTime(rs, "created_at")
            ),
            id
        );
        DocumentItem document = rows.isEmpty() ? null : rows.getFirst();
        if (document == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }
    private List<DocumentItem> loadDocuments() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, file_name, file_type, file_size, processing_status, summary, created_at FROM aieap.documents ORDER BY created_at DESC",
            (rs, rowNum) -> new DocumentItem(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("processing_status"),
                rs.getString("summary"),
                readOffsetDateTime(rs, "created_at")
            )
        );
    }

    private List<DocumentChunk> loadChunks(String documentId) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, chunk_index, content, citation_label FROM aieap.document_chunks WHERE document_id = ?::uuid ORDER BY chunk_index",
            (rs, rowNum) -> new DocumentChunk(
                rs.getString("id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("citation_label")
            ),
            documentId
        );
    }

    private RetrievalResult loadRelevantChunks(String documentId, String question, int limit) {
        List<DocumentChunk> vectorHits = loadRelevantChunksByVector(documentId, question, limit);
        if (!vectorHits.isEmpty()) {
            return new RetrievalResult(RetrievalMode.VECTOR, vectorHits);
        }

        List<DocumentChunk> sqlHits = loadRelevantChunksBySql(documentId, question, limit);
        if (!sqlHits.isEmpty()) {
            LOGGER.info("Falling back to SQL retrieval for document {}", documentId);
            return new RetrievalResult(RetrievalMode.SQL, sqlHits);
        }

        return new RetrievalResult(RetrievalMode.NONE, List.of());
    }

    private List<DocumentChunk> loadRelevantChunksBySql(String documentId, String question, int limit) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, chunk_index, content, citation_label FROM aieap.document_chunks " +
            "WHERE document_id = ?::uuid " +
            "ORDER BY ts_rank_cd(to_tsvector('english', content), plainto_tsquery('english', ?)) DESC, chunk_index ASC " +
            "LIMIT ?",
            (rs, rowNum) -> new DocumentChunk(
                rs.getString("id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("citation_label")
            ),
            documentId,
            question,
            limit
        );
    }

    private List<DocumentChunk> loadRelevantChunksByVector(String documentId, String question, int limit) {
        if (question == null || question.isBlank() || !llmClient.isConfigured() || !qdrantVectorStoreClient.isAvailable()) {
            return List.of();
        }

        try {
            List<Double> queryEmbedding = llmClient.embedding(question);
            if (queryEmbedding.isEmpty()) {
                return List.of();
            }

            List<String> vectorIds = qdrantVectorStoreClient.searchPointIds(queryEmbedding, documentId, limit);
            if (vectorIds.isEmpty()) {
                return List.of();
            }

            return loadChunksByVectorIds(documentId, vectorIds);
        } catch (Exception ex) {
            LOGGER.warn("Vector retrieval failed for document {}. Falling back to SQL retrieval", documentId, ex);
            return List.of();
        }
    }

    private List<DocumentChunk> loadChunksByVectorIds(String documentId, List<String> vectorIds) {
        JdbcTemplate db = requireJdbc();
        String placeholders = String.join(",", Collections.nCopies(vectorIds.size(), "?"));
        StringBuilder orderBy = new StringBuilder("CASE vector_id ");
        for (int i = 0; i < vectorIds.size(); i++) {
            orderBy.append("WHEN ? THEN ").append(i).append(" ");
        }
        orderBy.append("ELSE 9999 END");

        String sql = "SELECT id::text AS id, chunk_index, content, citation_label, vector_id FROM aieap.document_chunks " +
            "WHERE document_id = ?::uuid AND vector_id IN (" + placeholders + ") " +
            "ORDER BY " + orderBy;

        List<Object> args = new ArrayList<>();
        args.add(documentId);
        args.addAll(vectorIds);
        args.addAll(vectorIds);

        return db.query(
            sql,
            (rs, rowNum) -> new DocumentChunk(
                rs.getString("id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("citation_label")
            ),
            args.toArray()
        );
    }

    private void indexChunksInVectorStore(String documentId, List<String> chunkIds, List<String> chunks) {
        // Delegation to DocumentIndexingService.indexDocumentAsync() which runs in a
        // background thread. This method is retained for reference only; the async
        // service contains the active implementation.
        documentIndexingService.indexDocumentAsync(documentId, chunkIds, chunks);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file must not be empty");
        }
    }

    private String parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private int estimateTokenCount(String content) {
        return documentRagService.estimateTokenCount(content);
    }

    private OffsetDateTime readOffsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return rs.getTimestamp(column).toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * Generate citation label with position information.
     * Format: "source-index-position" (e.g., "upload-0-1234")
     */
    private String generateCitationLabel(String fileName, int chunkIndex, int charPosition) {
        String sourcePrefix = extractSourcePrefix(fileName);
        return sourcePrefix + "-" + chunkIndex + "-" + charPosition;
    }

    /**
     * Extract source prefix from file name for better citation tracking.
     * Examples: "report.pdf" → "report", "Q1_2024.docx" → "Q1_2024"
     */
    private String extractSourcePrefix(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "upload";
        }
        int dotIndex = fileName.lastIndexOf('.');
        String prefix = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return prefix.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public record DocumentItem(String id, String fileName, String fileType, long fileSize, String processingStatus, String summary, OffsetDateTime createdAt) {
    }

    public record DocumentChunk(String id, int chunkIndex, String content, String citationLabel) {
    }

    public record AskDocumentRequest(@NotBlank String question) {
    }

    public record DocumentAnswer(String question, String answer, double confidence, List<DocumentChunk> citations) {
    }

    public enum RetrievalMode {
        VECTOR,
        SQL,
        NONE
    }

    public record RetrievalResult(RetrievalMode mode, List<DocumentChunk> chunks) {
    }
}
