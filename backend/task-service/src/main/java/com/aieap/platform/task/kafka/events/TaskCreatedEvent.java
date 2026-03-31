package com.aieap.platform.task.kafka.events;

import java.time.OffsetDateTime;

public record TaskCreatedEvent(
    String eventId,
    String correlationId,
    String taskId,
    String title,
    String description,
    String assigneeUserId,
    String priority,
    String status,
    String source,
    String targetUserId,
    OffsetDateTime dueAt,
    String createdAt
) {}
