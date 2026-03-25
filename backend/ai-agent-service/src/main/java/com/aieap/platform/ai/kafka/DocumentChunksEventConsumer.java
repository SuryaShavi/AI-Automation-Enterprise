package com.aieap.platform.ai.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes document.chunks.ready events from document-service.
 * The document-service already indexes chunks into Qdrant during upload;
 * this consumer logs readiness and serves as an observability hook for
 * future AI pre-warming or cache invalidation use-cases.
 */
@Component
public class DocumentChunksEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunksEventConsumer.class);

    @KafkaListener(topics = "document.chunks.ready", groupId = "ai-agent-service")
    public void onDocumentChunksReady(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = correlationIdBytes != null
            ? new String(correlationIdBytes, StandardCharsets.UTF_8)
            : "unknown";

        String documentId = (String) event.get("documentId");
        String fileName = (String) event.getOrDefault("fileName", "");
        Object rawChunkCount = event.get("chunkCount");
        int chunkCount = rawChunkCount instanceof Number ? ((Number) rawChunkCount).intValue() : 0;
        String processingStatus = (String) event.getOrDefault("processingStatus", "");

        log.info(
            "[KAFKA] document.chunks.ready correlationId={} documentId={} fileName='{}' chunks={} status={}",
            correlationId, documentId, fileName, chunkCount, processingStatus
        );

        // Document chunks are already indexed in Qdrant by document-service.
        // This event signals the AI layer that RAG context for documentId is available.
        // Future: invalidate per-document answer cache, trigger pre-warming queries, etc.
        if ("RAG_READY".equals(processingStatus) && chunkCount > 0) {
            log.info("[KAFKA] Document {} is RAG_READY with {} chunks — Qdrant index available for queries",
                documentId, chunkCount);
        }
    }
}
