package com.aieap.platform.workflow;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Workflows")
public class WorkflowController {
    private final ConcurrentHashMap<String, WorkflowItem> workflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IntegrationItem> integrations = new ConcurrentHashMap<>();

    public WorkflowController() {
        workflows.put("workflow-1", new WorkflowItem("workflow-1", "Email to Task Automation", "ACTIVE", List.of("Email Received", "AI Task Extraction", "Task Created", "Notification Sent"), OffsetDateTime.now().minusDays(1)));
        integrations.put("gmail", new IntegrationItem("gmail", "CONNECTED", "OAuth2", OffsetDateTime.now().minusDays(2)));
        integrations.put("slack", new IntegrationItem("slack", "CONNECTED", "Webhook", OffsetDateTime.now().minusDays(3)));
        integrations.put("google-drive", new IntegrationItem("google-drive", "DISCONNECTED", "OAuth2", null));
    }

    @GetMapping("/workflows")
    public ApiEnvelope<List<WorkflowItem>> list(HttpServletRequest request) {
        return ResponseFactory.success(request, workflows.values().stream().toList());
    }

    @PostMapping("/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<WorkflowItem> create(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateWorkflowRequest createWorkflowRequest,
        HttpServletRequest request
    ) {
        String id = idempotencyKey == null ? UUID.randomUUID().toString() : "workflow-" + idempotencyKey;
        WorkflowItem workflow = new WorkflowItem(id, createWorkflowRequest.name(), "DRAFT", createWorkflowRequest.steps(), OffsetDateTime.now());
        workflows.put(id, workflow);
        return ResponseFactory.success(request, workflow);
    }

    @PatchMapping("/workflows/{id}/status")
    public ApiEnvelope<WorkflowItem> patchStatus(@PathVariable String id, @Valid @RequestBody WorkflowStatusRequest workflowStatusRequest, HttpServletRequest request) {
        String nextStatus = workflowStatusRequest.status().toUpperCase();
        if (!"ACTIVE".equals(nextStatus) && !"PAUSED".equals(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or PAUSED");
        }
        WorkflowItem workflow = workflow(id);
        WorkflowItem updated = new WorkflowItem(workflow.id(), workflow.name(), nextStatus, workflow.steps(), workflow.createdAt());
        workflows.put(id, updated);
        return ResponseFactory.success(request, updated);
    }

    @DeleteMapping("/workflows/{id}")
    public ApiEnvelope<Map<String, String>> delete(@PathVariable String id, HttpServletRequest request) {
        workflow(id);
        workflows.remove(id);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", id));
    }

    @GetMapping("/workflows/{id}/executions")
    public ApiEnvelope<List<Map<String, Object>>> executions(@PathVariable String id, HttpServletRequest request) {
        workflow(id);
        return ResponseFactory.success(request, List.of(
            Map.of("executionId", "exec-1", "status", "SUCCEEDED", "startedAt", OffsetDateTime.now().minusMinutes(30).toString(), "durationMs", 812),
            Map.of("executionId", "exec-2", "status", "SUCCEEDED", "startedAt", OffsetDateTime.now().minusHours(3).toString(), "durationMs", 923)
        ));
    }

    @GetMapping("/integrations")
    public ApiEnvelope<List<IntegrationItem>> listIntegrations(HttpServletRequest request) {
        return ResponseFactory.success(request, integrations.values().stream().toList());
    }

    @PostMapping("/integrations/{provider}/connect")
    public ApiEnvelope<IntegrationItem> connect(@PathVariable String provider, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        IntegrationItem updated = new IntegrationItem(provider, "CONNECTED", payload == null ? "OAuth2" : String.valueOf(payload.getOrDefault("authType", "OAuth2")), OffsetDateTime.now());
        integrations.put(provider, updated);
        return ResponseFactory.success(request, updated);
    }

    @PostMapping("/integrations/{provider}/disconnect")
    public ApiEnvelope<IntegrationItem> disconnect(@PathVariable String provider, HttpServletRequest request) {
        IntegrationItem updated = new IntegrationItem(provider, "DISCONNECTED", "OAuth2", null);
        integrations.put(provider, updated);
        return ResponseFactory.success(request, updated);
    }

    @PostMapping("/integrations/webhooks/{provider}")
    public ApiEnvelope<Map<String, String>> webhook(@PathVariable String provider, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        return ResponseFactory.success(request, Map.of("provider", provider, "status", "accepted", "received", String.valueOf(payload != null)));
    }

    private WorkflowItem workflow(String id) {
        WorkflowItem workflow = workflows.get(id);
        if (workflow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        return workflow;
    }

    public record WorkflowItem(String id, String name, String status, List<String> steps, OffsetDateTime createdAt) {
    }

    public record CreateWorkflowRequest(@NotBlank String name, List<String> steps) {
    }

    public record WorkflowStatusRequest(@NotBlank String status) {
    }

    public record IntegrationItem(String provider, String status, String authType, OffsetDateTime connectedAt) {
    }
}