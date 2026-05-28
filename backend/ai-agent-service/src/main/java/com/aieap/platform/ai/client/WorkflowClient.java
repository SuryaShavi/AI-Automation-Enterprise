package com.aieap.platform.ai.client;

import com.aieap.platform.common.ApiEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for workflow-service.
 * Enables AI Agent to query and trigger workflows on-demand.
 */
@FeignClient(name = "workflow-service", path = "/workflows")
public interface WorkflowClient {

    @GetMapping
    ApiEnvelope<List<WorkflowItem>> list();

    @GetMapping("/{id}")
    ApiEnvelope<WorkflowItem> get(@PathVariable String id);

    @PostMapping("/{id}/run")
    ApiEnvelope<WorkflowExecutionItem> run(@PathVariable String id);

    @GetMapping("/{id}/executions")
    ApiEnvelope<List<WorkflowExecutionItem>> executions(
        @PathVariable String id,
        @RequestParam(defaultValue = "20") int limit
    );

    @PatchMapping("/{id}/status")
    ApiEnvelope<WorkflowItem> updateStatus(
        @PathVariable String id,
        @RequestBody Map<String, String> statusUpdate
    );

    // DTOs mirroring workflow-service
    record WorkflowItem(
        String id,
        String name,
        String description,
        String status,
        String scheduleCron,
        String eventType,
        String provider,
        Map<String, String> config,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}

    record WorkflowExecutionItem(
        String id,
        String workflowId,
        String status,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Map<String, Object> input,
        Map<String, Object> output
    ) {}
}
