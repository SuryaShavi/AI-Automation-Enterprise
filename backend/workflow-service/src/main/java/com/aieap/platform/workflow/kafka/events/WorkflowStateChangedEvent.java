package com.aieap.platform.workflow.kafka.events;

public record WorkflowStateChangedEvent(
    String eventId,
    String correlationId,
    String targetUserId,
    String workflowId,
    String workflowName,
    String previousStatus,
    String newStatus,
    String occurredAt
) {}
