package com.aieap.platform.workflow;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class WorkflowApi {

    private WorkflowApi() {
    }

    public record WorkflowItem(
        String id,
        String name,
        String status,
        List<String> steps,
        OffsetDateTime createdAt,
        List<WorkflowTriggerItem> triggers
    ) {
    }

    public record WorkflowTriggerItem(
        String id,
        String type,
        String scheduleCron,
        String eventType,
        String provider,
        boolean enabled,
        OffsetDateTime lastFiredAt
    ) {
    }

    public record WorkflowStepResult(
        int index,
        String name,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String message
    ) {
    }

    public record WorkflowExecutionItem(
        String executionId,
        String status,
        String triggerType,
        String triggerLabel,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int durationMs,
        String errorMessage,
        List<WorkflowStepResult> stepResults
    ) {
    }

    public record CreateWorkflowRequest(
        @NotBlank String name,
        List<String> steps,
        List<WorkflowTriggerRequest> triggers
    ) {
    }

    public record WorkflowStatusRequest(@NotBlank String status) {
    }

    public record WorkflowUpdateRequest(
        String status,
        String name,
        List<WorkflowTriggerRequest> triggers
    ) {
    }

    public record WorkflowTriggerRequest(
        String type,
        String scheduleCron,
        String eventType,
        String provider,
        String secret,
        Boolean enabled,
        Map<String, Object> payloadFilter
    ) {
    }

    public record IntegrationItem(
        String provider,
        String status,
        String authType,
        OffsetDateTime connectedAt,
        boolean webhookSecretConfigured
    ) {
    }

    public record WorkflowEventRequest(
        @NotBlank String eventType,
        String source,
        Map<String, Object> payload
    ) {
    }

    public record WebhookReceipt(
        String provider,
        String eventType,
        int matchedTriggers,
        int executionsStarted,
        boolean signatureValidated,
        OffsetDateTime receivedAt
    ) {
    }
}