package com.aieap.platform.document.kafka.events;

public record DocumentChunksReadyEvent(
    String eventId,
    String correlationId,
    String documentId,
    String fileName,
    int chunkCount,
    String processingStatus,
    String ownerUserId,
    String occurredAt
) {}
