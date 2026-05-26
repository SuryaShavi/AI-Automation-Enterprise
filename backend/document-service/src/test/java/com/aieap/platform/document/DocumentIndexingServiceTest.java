package com.aieap.platform.document;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aieap.platform.common.ai.LlmClient;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class DocumentIndexingServiceTest {

    private LlmClient llmClient;
    private QdrantVectorStoreClient qdrantVectorStoreClient;
    private JdbcTemplate jdbcTemplate;
    private DocumentIndexingService service;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = Mockito.mock(LlmClient.class);
        qdrantVectorStoreClient = Mockito.mock(QdrantVectorStoreClient.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        service = new DocumentIndexingService(llmClient, qdrantVectorStoreClient);

        Field jdbcTemplateField = DocumentIndexingService.class.getDeclaredField("jdbcTemplate");
        jdbcTemplateField.setAccessible(true);
        jdbcTemplateField.set(service, jdbcTemplate);
    }

    @Test
    void indexDocumentAsyncUpsertsVectorsAndUpdatesChunkVectorIdsAndStatus() {
        String documentId = "11111111-1111-1111-1111-111111111111";
        List<String> chunkIds = List.of(
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333"
        );
        List<String> chunks = List.of("chunk content 1", "chunk content 2");

        when(llmClient.isConfigured()).thenReturn(true);
        when(qdrantVectorStoreClient.isAvailable()).thenReturn(true);
        when(llmClient.embedding("chunk content 1")).thenReturn(List.of(0.1, 0.2, 0.3));
        when(llmClient.embedding("chunk content 2")).thenReturn(List.of(0.4, 0.5, 0.6));

        service.indexDocumentAsync(documentId, chunkIds, chunks);

        verify(qdrantVectorStoreClient).ensureCollection(eq(3));

        ArgumentCaptor<String> pointIdCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(qdrantVectorStoreClient, Mockito.times(2)).upsertPoint(
            pointIdCaptor.capture(),
            Mockito.anyList(),
            payloadCaptor.capture()
        );

        List<String> capturedPointIds = pointIdCaptor.getAllValues();
        List<Map<String, Object>> capturedPayloads = payloadCaptor.getAllValues();

        org.junit.jupiter.api.Assertions.assertEquals(List.of(
            documentId + "-0",
            documentId + "-1"
        ), capturedPointIds);

        org.junit.jupiter.api.Assertions.assertEquals(documentId, capturedPayloads.get(0).get("documentId"));
        org.junit.jupiter.api.Assertions.assertEquals(chunkIds.get(0), capturedPayloads.get(0).get("chunkId"));
        org.junit.jupiter.api.Assertions.assertEquals(0, capturedPayloads.get(0).get("chunkIndex"));

        org.junit.jupiter.api.Assertions.assertEquals(documentId, capturedPayloads.get(1).get("documentId"));
        org.junit.jupiter.api.Assertions.assertEquals(chunkIds.get(1), capturedPayloads.get(1).get("chunkId"));
        org.junit.jupiter.api.Assertions.assertEquals(1, capturedPayloads.get(1).get("chunkIndex"));

        verify(jdbcTemplate).update(
            eq("UPDATE aieap.document_chunks SET vector_id = ? WHERE id = ?::uuid"),
            eq(documentId + "-0"),
            eq(chunkIds.get(0))
        );
        verify(jdbcTemplate).update(
            eq("UPDATE aieap.document_chunks SET vector_id = ? WHERE id = ?::uuid"),
            eq(documentId + "-1"),
            eq(chunkIds.get(1))
        );

        verify(jdbcTemplate).update(
            eq("UPDATE aieap.documents SET processing_status = ?, updated_at = NOW() WHERE id = ?::uuid"),
            eq("READY"),
            eq(documentId)
        );
    }

    @Test
    void indexDocumentAsyncMarksIndexedNoVectorWhenDependenciesUnavailable() {
        String documentId = "11111111-1111-1111-1111-111111111111";

        when(llmClient.isConfigured()).thenReturn(false);
        when(qdrantVectorStoreClient.isAvailable()).thenReturn(true);

        service.indexDocumentAsync(documentId, List.of("chunk-id"), List.of("chunk"));

        verify(jdbcTemplate).update(
            eq("UPDATE aieap.documents SET processing_status = ?, updated_at = NOW() WHERE id = ?::uuid"),
            eq("INDEXED_NO_VECTOR"),
            eq(documentId)
        );
        Mockito.verifyNoInteractions(qdrantVectorStoreClient);
    }
}
