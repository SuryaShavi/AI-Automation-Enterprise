package com.aieap.platform.report.kafka.events;

public record ReportGeneratedEvent(
    String eventId,
    String correlationId,
    String targetUserId,
    String reportId,
    String reportType,
    String title,
    String status,
    String occurredAt
) {}
