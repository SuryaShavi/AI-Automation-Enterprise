package com.aieap.platform.workflow;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.AllowedValues;
import com.aieap.platform.workflow.WorkflowApi.CreateWorkflowRequest;
import com.aieap.platform.workflow.WorkflowApi.IntegrationItem;
import com.aieap.platform.workflow.WorkflowApi.WebhookReceipt;
import com.aieap.platform.workflow.WorkflowApi.WorkflowEventRequest;
import com.aieap.platform.workflow.WorkflowApi.WorkflowExecutionItem;
import com.aieap.platform.workflow.WorkflowApi.WorkflowItem;
import com.aieap.platform.workflow.WorkflowApi.WorkflowStatusRequest;
import com.aieap.platform.workflow.WorkflowApi.WorkflowTriggerItem;
import com.aieap.platform.workflow.WorkflowApi.WorkflowTriggerRequest;
import com.aieap.platform.workflow.WorkflowApi.WorkflowUpdateRequest;
import com.aieap.platform.workflow.kafka.KafkaEventPublisher;
import com.aieap.platform.workflow.kafka.events.WorkflowStateChangedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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

    private static final List<String> DEFAULT_PROVIDERS = List.of("Gmail", "Outlook", "Slack", "Google Drive", "Webhook");

    private final ObjectMapper objectMapper;
    private final WorkflowRuntimeService workflowRuntimeService;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    public WorkflowController(ObjectMapper objectMapper, WorkflowRuntimeService workflowRuntimeService) {
        this.objectMapper = objectMapper;
        this.workflowRuntimeService = workflowRuntimeService;
    }

    @GetMapping("/workflows")
    public ApiEnvelope<List<WorkflowItem>> list(HttpServletRequest request) {
        return ResponseFactory.success(request, loadWorkflows());
    }

    @PostMapping("/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<WorkflowItem> create(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateWorkflowRequest createWorkflowRequest,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String workflowId = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        List<String> steps = normalizeSteps(createWorkflowRequest.steps());
        List<WorkflowTriggerRequest> triggers = normalizeTriggerRequests(createWorkflowRequest.triggers());

        db.update(
            "INSERT INTO aieap.workflows (id, name, status, trigger_type, config_json, created_at, updated_at) VALUES (?::uuid, ?, 'DRAFT', ?, '{}'::jsonb, NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            workflowId,
            InputSanitizer.requiredText(createWorkflowRequest.name()),
            deriveWorkflowTriggerType(triggers)
        );

        for (int index = 0; index < steps.size(); index++) {
            db.update(
                "INSERT INTO aieap.workflow_steps (id, workflow_id, step_index, step_name, created_at) VALUES (?::uuid, ?::uuid, ?, ?, NOW())",
                UUID.randomUUID().toString(),
                workflowId,
                index,
                steps.get(index)
            );
        }

        replaceTriggers(workflowId, triggers);
        return ResponseFactory.success(request, workflow(workflowId));
    }

    @PatchMapping("/workflows/{id}")
    @Transactional
    public ApiEnvelope<WorkflowItem> update(@PathVariable UUID id, @Valid @RequestBody WorkflowUpdateRequest workflowUpdateRequest, HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        String workflowId = id.toString();
        WorkflowItem before = workflow(workflowId);
        String normalizedStatus = null;

        if (workflowUpdateRequest.status() != null) {
            normalizedStatus = normalizeWorkflowStatus(workflowUpdateRequest.status(), true);
            int updated = db.update("UPDATE aieap.workflows SET status = ?, updated_at = NOW() WHERE id = ?::uuid", normalizedStatus, workflowId);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
            }
        }

        if (workflowUpdateRequest.name() != null && !workflowUpdateRequest.name().isBlank()) {
            db.update("UPDATE aieap.workflows SET name = ?, updated_at = NOW() WHERE id = ?::uuid", InputSanitizer.requiredText(workflowUpdateRequest.name()), workflowId);
        }

        if (workflowUpdateRequest.triggers() != null) {
            List<WorkflowTriggerRequest> triggers = normalizeTriggerRequests(workflowUpdateRequest.triggers());
            replaceTriggers(workflowId, triggers);
            db.update("UPDATE aieap.workflows SET trigger_type = ?, updated_at = NOW() WHERE id = ?::uuid", deriveWorkflowTriggerType(triggers), workflowId);
        }

        WorkflowItem after = workflow(workflowId);
        publishWorkflowStateChangeIfNeeded(before, after, normalizedStatus, request);
        return ResponseFactory.success(request, after);
    }

    @PatchMapping("/workflows/{id}/status")
    @Transactional
    public ApiEnvelope<WorkflowItem> patchStatus(@PathVariable UUID id, @Valid @RequestBody WorkflowStatusRequest workflowStatusRequest, HttpServletRequest request) {
        String workflowId = id.toString();
        WorkflowItem before = workflow(workflowId);
        String normalizedStatus = normalizeWorkflowStatus(workflowStatusRequest.status(), false);
        int updated = requireJdbc().update("UPDATE aieap.workflows SET status = ?, updated_at = NOW() WHERE id = ?::uuid", normalizedStatus, workflowId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }

        WorkflowItem after = workflow(workflowId);
        publishWorkflowStateChangeIfNeeded(before, after, normalizedStatus, request);
        return ResponseFactory.success(request, after);
    }

    @DeleteMapping("/workflows/{id}")
    @Transactional
    public ApiEnvelope<Map<String, String>> delete(@PathVariable UUID id, HttpServletRequest request) {
        String workflowId = id.toString();
        workflow(workflowId);
        requireJdbc().update("DELETE FROM aieap.workflows WHERE id = ?::uuid", workflowId);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", workflowId));
    }

    @PostMapping("/workflows/{id}/run")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<WorkflowExecutionItem> run(@PathVariable UUID id, HttpServletRequest request) {
        String workflowId = id.toString();
        workflow(workflowId);
        WorkflowExecutionItem execution = workflowRuntimeService.startWorkflowExecution(workflowId, "MANUAL", "Manual run", Map.of(), resolveCorrelationId(request));
        return ResponseFactory.success(request, execution);
    }

    @GetMapping("/workflows/{id}/executions")
    public ApiEnvelope<List<WorkflowExecutionItem>> executions(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseFactory.success(request, workflowRuntimeService.listExecutions(id.toString()));
    }

    @PostMapping("/workflows/events")
    public ApiEnvelope<Map<String, Object>> ingestEvent(@Valid @RequestBody WorkflowEventRequest workflowEventRequest, HttpServletRequest request) {
        int executionsStarted = workflowRuntimeService.processEvent(
            workflowEventRequest.eventType(),
            workflowEventRequest.source(),
            workflowEventRequest.payload() == null ? Map.of() : workflowEventRequest.payload(),
            resolveCorrelationId(request)
        );
        return ResponseFactory.success(request, Map.of(
            "eventType", workflowEventRequest.eventType(),
            "executionsStarted", executionsStarted,
            "receivedAt", OffsetDateTime.now().toString()
        ));
    }

    @GetMapping("/integrations")
    public ApiEnvelope<List<IntegrationItem>> listIntegrations(HttpServletRequest request) {
        String userId = resolveFirstActiveUserId();
        if (userId == null) {
            return ResponseFactory.success(request, DEFAULT_PROVIDERS.stream()
                .map(provider -> new IntegrationItem(provider, "DISCONNECTED", defaultAuthType(provider), null, false))
                .toList());
        }

        List<IntegrationItem> storedItems = requireJdbc().query(
            "SELECT provider, status, auth_type, connected_at, config_json::text AS config_json FROM aieap.integrations WHERE user_id = ?::uuid ORDER BY provider",
            (rs, rowNum) -> {
                Map<String, Object> config = readJsonMap(rs.getString("config_json"));
                return new IntegrationItem(
                    rs.getString("provider"),
                    rs.getString("status"),
                    rs.getString("auth_type"),
                    rs.getObject("connected_at", OffsetDateTime.class),
                    config.get("webhookSecret") instanceof String secret && !secret.isBlank()
                );
            },
            userId
        );
        return ResponseFactory.success(request, mergeIntegrationItems(storedItems));
    }

    @PostMapping("/integrations/{provider}/connect")
    @Transactional
    public ApiEnvelope<IntegrationItem> connect(@PathVariable @AllowedValues(values = {"Gmail", "Outlook", "Slack", "Google Drive", "Webhook"}, allowNull = false) String provider, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        String userId = defaultUserId();
        String normalizedProvider = canonicalProvider(provider);
        Map<String, Object> config = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        String authType = String.valueOf(config.getOrDefault("authType", defaultAuthType(normalizedProvider)));
        requireJdbc().update(
            "INSERT INTO aieap.integrations (id, user_id, provider, status, auth_type, config_json, connected_at, updated_at) VALUES (?::uuid, ?::uuid, ?, 'CONNECTED', ?, ?::jsonb, NOW(), NOW()) " +
                "ON CONFLICT (user_id, provider) DO UPDATE SET status = 'CONNECTED', auth_type = EXCLUDED.auth_type, config_json = EXCLUDED.config_json, connected_at = NOW(), disconnected_at = NULL, updated_at = NOW()",
            UUID.randomUUID().toString(),
            userId,
            normalizedProvider,
            authType,
            toJson(config)
        );
        return ResponseFactory.success(request, integration(userId, normalizedProvider));
    }

    @PostMapping("/integrations/{provider}/disconnect")
    @Transactional
    public ApiEnvelope<IntegrationItem> disconnect(@PathVariable @AllowedValues(values = {"Gmail", "Outlook", "Slack", "Google Drive", "Webhook"}, allowNull = false) String provider, HttpServletRequest request) {
        String userId = defaultUserId();
        String normalizedProvider = canonicalProvider(provider);
        requireJdbc().update(
            "UPDATE aieap.integrations SET status = 'DISCONNECTED', disconnected_at = NOW(), connected_at = NULL, updated_at = NOW() WHERE user_id = ?::uuid AND provider = ?",
            userId,
            normalizedProvider
        );
        return ResponseFactory.success(request, integration(userId, normalizedProvider));
    }

    @PostMapping("/integrations/webhooks/{provider}")
    public ApiEnvelope<WebhookReceipt> webhook(
        @PathVariable @AllowedValues(values = {"Gmail", "Outlook", "Slack", "Google Drive", "Webhook"}, allowNull = false) String provider,
        @RequestBody(required = false) Map<String, Object> payload,
        @RequestHeader Map<String, String> headers,
        HttpServletRequest request
    ) {
        String normalizedProvider = canonicalProvider(provider);
        Map<String, Object> body = payload == null ? Map.of() : payload;
        WebhookReceipt receipt = workflowRuntimeService.processWebhook(normalizedProvider, body, headers, toJson(body), resolveCorrelationId(request));
        return ResponseFactory.success(request, receipt);
    }

    private WorkflowItem workflow(String id) {
        List<WorkflowItem> rows = requireJdbc().query(
            "SELECT id::text AS id, name, status, created_at FROM aieap.workflows WHERE id = ?::uuid",
            (rs, rowNum) -> new WorkflowItem(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("status"),
                loadWorkflowSteps(rs.getString("id")),
                rs.getObject("created_at", OffsetDateTime.class),
                loadWorkflowTriggers(rs.getString("id"))
            ),
            id
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        return rows.getFirst();
    }

    private List<WorkflowItem> loadWorkflows() {
        return requireJdbc().query(
            "SELECT id::text AS id, name, status, created_at FROM aieap.workflows ORDER BY created_at DESC",
            (rs, rowNum) -> new WorkflowItem(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("status"),
                loadWorkflowSteps(rs.getString("id")),
                rs.getObject("created_at", OffsetDateTime.class),
                loadWorkflowTriggers(rs.getString("id"))
            )
        );
    }

    private List<String> loadWorkflowSteps(String workflowId) {
        return requireJdbc().query(
            "SELECT step_name FROM aieap.workflow_steps WHERE workflow_id = ?::uuid ORDER BY step_index",
            (rs, rowNum) -> rs.getString("step_name"),
            workflowId
        );
    }

    private List<WorkflowTriggerItem> loadWorkflowTriggers(String workflowId) {
        return requireJdbc().query(
            "SELECT id::text AS id, trigger_type, schedule_cron, event_type, enabled, last_fired_at, config_json::text AS config_json FROM aieap.workflow_triggers WHERE workflow_id = ?::uuid ORDER BY created_at",
            (rs, rowNum) -> {
                Map<String, Object> config = readJsonMap(rs.getString("config_json"));
                return new WorkflowTriggerItem(
                    rs.getString("id"),
                    rs.getString("trigger_type"),
                    rs.getString("schedule_cron"),
                    rs.getString("event_type"),
                    config.get("provider") == null ? null : String.valueOf(config.get("provider")),
                    rs.getBoolean("enabled"),
                    rs.getObject("last_fired_at", OffsetDateTime.class)
                );
            },
            workflowId
        );
    }

    private void replaceTriggers(String workflowId, List<WorkflowTriggerRequest> triggers) {
        requireJdbc().update("DELETE FROM aieap.workflow_triggers WHERE workflow_id = ?::uuid", workflowId);
        for (WorkflowTriggerRequest trigger : triggers) {
            String type = normalizeTriggerType(trigger.type());
            validateTrigger(type, trigger);
            requireJdbc().update(
                "INSERT INTO aieap.workflow_triggers (id, workflow_id, trigger_type, schedule_cron, event_type, config_json, enabled, created_at, updated_at) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?, NOW(), NOW())",
                UUID.randomUUID().toString(),
                workflowId,
                type,
                blankToNull(trigger.scheduleCron()),
                blankToNull(trigger.eventType()),
                toJson(buildTriggerConfig(trigger)),
                trigger.enabled() == null || trigger.enabled()
            );
        }
    }

    private void validateTrigger(String type, WorkflowTriggerRequest trigger) {
        if ("SCHEDULE".equals(type) && blankToNull(trigger.scheduleCron()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled triggers require scheduleCron");
        }
        if ("EVENT".equals(type) && blankToNull(trigger.eventType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event triggers require eventType");
        }
        if ("WEBHOOK".equals(type) && blankToNull(trigger.provider()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook triggers require provider");
        }
    }

    private Map<String, Object> buildTriggerConfig(WorkflowTriggerRequest trigger) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (blankToNull(trigger.provider()) != null) {
            config.put("provider", trigger.provider().trim());
        }
        if (blankToNull(trigger.secret()) != null) {
            config.put("secret", trigger.secret());
        }
        if (trigger.payloadFilter() != null && !trigger.payloadFilter().isEmpty()) {
            config.put("payloadFilter", trigger.payloadFilter());
        }
        if (blankToNull(trigger.eventType()) != null && "WEBHOOK".equals(normalizeTriggerType(trigger.type()))) {
            config.put("eventType", trigger.eventType().trim());
        }
        return config;
    }

    private List<WorkflowTriggerRequest> normalizeTriggerRequests(List<WorkflowTriggerRequest> triggers) {
        if (triggers == null) {
            return List.of();
        }
        List<WorkflowTriggerRequest> normalized = new ArrayList<>();
        for (WorkflowTriggerRequest trigger : triggers) {
            if (trigger == null || blankToNull(trigger.type()) == null) {
                continue;
            }
            normalized.add(trigger);
        }
        return normalized;
    }

    private List<String> normalizeSteps(List<String> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
            .filter(step -> step != null && !step.isBlank())
            .map(String::trim)
            .toList();
    }

    private void publishWorkflowStateChangeIfNeeded(WorkflowItem before, WorkflowItem after, String normalizedStatus, HttpServletRequest request) {
        if (normalizedStatus == null || kafkaEventPublisher == null || before.status().equals(after.status())) {
            return;
        }
        String correlationId = resolveCorrelationId(request);
        kafkaEventPublisher.publish(
            "workflow.state_changed",
            after.id(),
            new WorkflowStateChangedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                resolveFirstActiveUserId(),
                after.id(),
                after.name(),
                before.status(),
                after.status(),
                OffsetDateTime.now().toString()
            ),
            correlationId
        );
    }

    private List<IntegrationItem> mergeIntegrationItems(List<IntegrationItem> storedItems) {
        Map<String, IntegrationItem> byProvider = new LinkedHashMap<>();
        for (String provider : DEFAULT_PROVIDERS) {
            byProvider.put(provider.toLowerCase(Locale.ROOT), new IntegrationItem(provider, "DISCONNECTED", defaultAuthType(provider), null, false));
        }
        for (IntegrationItem item : storedItems) {
            byProvider.put(item.provider().toLowerCase(Locale.ROOT), item);
        }
        return new ArrayList<>(byProvider.values());
    }

    private IntegrationItem integration(String userId, String provider) {
        List<IntegrationItem> rows = requireJdbc().query(
            "SELECT provider, status, auth_type, connected_at, config_json::text AS config_json FROM aieap.integrations WHERE user_id = ?::uuid AND provider = ?",
            (rs, rowNum) -> {
                Map<String, Object> config = readJsonMap(rs.getString("config_json"));
                return new IntegrationItem(
                    rs.getString("provider"),
                    rs.getString("status"),
                    rs.getString("auth_type"),
                    rs.getObject("connected_at", OffsetDateTime.class),
                    config.get("webhookSecret") instanceof String secret && !secret.isBlank()
                );
            },
            userId,
            provider
        );
        if (rows.isEmpty()) {
            return new IntegrationItem(provider, "DISCONNECTED", defaultAuthType(provider), null, false);
        }
        return rows.getFirst();
    }

    private String defaultAuthType(String provider) {
        return "Webhook".equalsIgnoreCase(provider) ? "HMAC" : "OAuth2";
    }

    private String canonicalProvider(String provider) {
        for (String supported : DEFAULT_PROVIDERS) {
            if (supported.equalsIgnoreCase(provider)) {
                return supported;
            }
        }
        return provider;
    }

    private String deriveWorkflowTriggerType(List<WorkflowTriggerRequest> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return "MANUAL";
        }
        return normalizeTriggerType(triggers.getFirst().type());
    }

    private String normalizeWorkflowStatus(String rawStatus, boolean allowDraft) {
        String normalized = rawStatus == null ? null : rawStatus.trim().toUpperCase(Locale.ROOT);
        List<String> allowed = allowDraft ? List.of("DRAFT", "ACTIVE", "PAUSED") : List.of("ACTIVE", "PAUSED");
        if (normalized == null || !allowed.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be " + String.join(", ", allowed));
        }
        return normalized;
    }

    private String normalizeTriggerType(String rawType) {
        String normalized = rawType == null ? null : rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !List.of("MANUAL", "SCHEDULE", "EVENT", "WEBHOOK").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trigger type must be MANUAL, SCHEDULE, EVENT, or WEBHOOK");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultUserId() {
        String userId = resolveFirstActiveUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active user available for workflow integrations");
        }
        return userId;
    }

    private String resolveFirstActiveUserId() {
        return requireJdbc().query(
            "SELECT id::text AS id FROM aieap.users WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
            rs -> rs.next() ? rs.getString("id") : null
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        return request.getHeader("X-Correlation-ID") != null ? request.getHeader("X-Correlation-ID") : UUID.randomUUID().toString();
    }

    private Map<String, Object> readJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to parse workflow metadata", exception);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode workflow payload", exception);
        }
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }
}