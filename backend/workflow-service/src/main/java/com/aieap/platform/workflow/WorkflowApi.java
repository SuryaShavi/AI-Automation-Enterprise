package com.aieap.platform.workflow;

import com.aieap.platform.common.validation.NullOrNotBlank;
import com.aieap.platform.workflow.validation.ValidWorkflowTrigger;
import com.aieap.platform.workflow.validation.ValidWorkflowUpdate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        @NotBlank @Size(max = 120) String name,
        @Size(max = 50) List<@NotBlank @Size(max = 120) String> steps,
        @Size(max = 10) List<@Valid WorkflowTriggerRequest> triggers
    ) {
    }

    public record WorkflowStatusRequest(@NotBlank String status) {
    }

    @ValidWorkflowUpdate
    public record WorkflowUpdateRequest(
        @NullOrNotBlank String status,
        @NullOrNotBlank @Size(max = 120) String name,
        @Size(max = 10) List<@Valid WorkflowTriggerRequest> triggers
    ) {
    }

    @ValidWorkflowTrigger
    public record WorkflowTriggerRequest(
        @NullOrNotBlank String type,
        @NullOrNotBlank @Size(max = 120) String scheduleCron,
        @NullOrNotBlank @Size(max = 120) String eventType,
        @NullOrNotBlank @Size(max = 80) String provider,
        @NullOrNotBlank @Size(max = 160) String secret,
        Boolean enabled,
        @Size(max = 20) Map<String, Object> payloadFilter
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
        @NotBlank @Size(max = 120) String eventType,
        @NullOrNotBlank @Size(max = 120) String source,
        @Size(max = 25) Map<String, Object> payload
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