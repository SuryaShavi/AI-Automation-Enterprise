package com.aieap.platform.email.kafka.events;

public record EmailIngestedEvent(
    String eventId,
    String correlationId,
    String targetUserId,
    String emailId,
    String senderEmail,
    String senderName,
    String subject,
    String priority,
    String occurredAt
) {}
