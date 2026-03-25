package com.aieap.platform.email.kafka.events;

public record ExtractedTaskCreatedEvent(
    String eventId,
    String correlationId,
    String emailId,
    String taskId,
    String suggestedTitle,
    double confidence,
    String occurredAt
) {}
