package com.aieap.platform.workflow;

import com.aieap.platform.workflow.WorkflowApi.WebhookReceipt;
import com.aieap.platform.workflow.WorkflowApi.WorkflowExecutionItem;
import com.aieap.platform.workflow.WorkflowApi.WorkflowStepResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public WorkflowRuntimeService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    public WorkflowExecutionItem startWorkflowExecution(
        String workflowId,
        String triggerType,
        String triggerLabel,
        Map<String, Object> payload,
        String correlationId
    ) {
        WorkflowDefinition workflow = loadWorkflow(workflowId);
        if (!"MANUAL".equalsIgnoreCase(triggerType) && !"ACTIVE".equalsIgnoreCase(workflow.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow must be ACTIVE for automatic triggers");
        }

        String executionId = UUID.randomUUID().toString();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> initialStepStates = new ArrayList<>();
        for (int index = 0; index < workflow.steps().size(); index++) {
            initialStepStates.add(stepState(index, workflow.steps().get(index), "PENDING", null, null, null));
        }

        Map<String, Object> initialResult = buildResultPayload(triggerType, triggerLabel, correlationId, payload, initialStepStates, -1);
        jdbcTemplate.update(
            "INSERT INTO aieap.workflow_executions (id, workflow_id, status, started_at, duration_ms, result_json) VALUES (?::uuid, ?::uuid, 'PENDING', ?, 0, ?::jsonb)",
            executionId,
            workflowId,
            startedAt,
            toJson(initialResult)
        );

        taskExecutor.execute(() -> executeWorkflow(executionId, workflow, triggerType, triggerLabel, payload, correlationId, startedAt));
        return mapExecution(executionId, "PENDING", triggerType, triggerLabel, startedAt, null, 0, null, initialStepStates);
    }

    public List<WorkflowExecutionItem> listExecutions(String workflowId) {
        loadWorkflow(workflowId);
        return jdbcTemplate.query(
            "SELECT id::text AS id, status, started_at, completed_at, duration_ms, error_message, result_json::text AS result_json FROM aieap.workflow_executions WHERE workflow_id = ?::uuid ORDER BY started_at DESC",
            (rs, rowNum) -> {
                Map<String, Object> resultJson = readJsonMap(rs.getString("result_json"));
                return mapExecution(
                    rs.getString("id"),
                    rs.getString("status"),
                    String.valueOf(resultJson.getOrDefault("triggerType", "MANUAL")),
                    String.valueOf(resultJson.getOrDefault("triggerLabel", "Manual run")),
                    rs.getObject("started_at", OffsetDateTime.class),
                    rs.getObject("completed_at", OffsetDateTime.class),
                    rs.getInt("duration_ms"),
                    rs.getString("error_message"),
                    extractStepResults(resultJson)
                );
            },
            workflowId
        );
    }

    public int processDueScheduledTriggers() {
        List<TriggerDefinition> triggers = loadTriggers(
            "SELECT t.id::text AS id, t.workflow_id::text AS workflow_id, w.name AS workflow_name, w.status AS workflow_status, t.trigger_type, t.schedule_cron, t.event_type, t.config_json::text AS config_json, t.last_fired_at, t.created_at " +
                "FROM aieap.workflow_triggers t JOIN aieap.workflows w ON w.id = t.workflow_id WHERE t.enabled = TRUE AND t.trigger_type = 'SCHEDULE'"
        );

        int executions = 0;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (TriggerDefinition trigger : triggers) {
            if (!"ACTIVE".equalsIgnoreCase(trigger.workflowStatus()) || !isDue(trigger, now)) {
                continue;
            }
            touchTrigger(trigger.id(), now);
            startWorkflowExecution(trigger.workflowId(), "SCHEDULE", "Scheduled: " + trigger.scheduleCron(), Map.of("scheduleCron", trigger.scheduleCron()), UUID.randomUUID().toString());
            executions++;
        }
        return executions;
    }

    public int processEvent(String eventType, String source, Map<String, Object> payload, String correlationId) {
        List<TriggerDefinition> triggers = loadTriggers(
            "SELECT t.id::text AS id, t.workflow_id::text AS workflow_id, w.name AS workflow_name, w.status AS workflow_status, t.trigger_type, t.schedule_cron, t.event_type, t.config_json::text AS config_json, t.last_fired_at, t.created_at " +
                "FROM aieap.workflow_triggers t JOIN aieap.workflows w ON w.id = t.workflow_id WHERE t.enabled = TRUE AND t.trigger_type = 'EVENT' AND t.event_type = ?",
            eventType
        );

        int executions = 0;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (TriggerDefinition trigger : triggers) {
            if (!"ACTIVE".equalsIgnoreCase(trigger.workflowStatus()) || !matchesSource(trigger.config(), source) || !matchesPayloadFilter(trigger.config(), payload)) {
                continue;
            }
            touchTrigger(trigger.id(), now);
            startWorkflowExecution(trigger.workflowId(), "EVENT", eventType, payload == null ? Map.of() : payload, correlationId);
            executions++;
        }
        return executions;
    }

    public WebhookReceipt processWebhook(String provider, Map<String, Object> payload, Map<String, String> headers, String payloadJson, String correlationId) {
        List<TriggerDefinition> triggers = loadTriggers(
            "SELECT t.id::text AS id, t.workflow_id::text AS workflow_id, w.name AS workflow_name, w.status AS workflow_status, t.trigger_type, t.schedule_cron, t.event_type, t.config_json::text AS config_json, t.last_fired_at, t.created_at " +
                "FROM aieap.workflow_triggers t JOIN aieap.workflows w ON w.id = t.workflow_id WHERE t.enabled = TRUE AND t.trigger_type = 'WEBHOOK' AND LOWER(COALESCE(t.config_json->>'provider', '')) = LOWER(?)",
            provider
        );

        String eventType = extractEventType(payload);
        String signature = findSignatureHeader(headers);
        String integrationSecret = loadConnectedIntegrationSecret(provider);
        boolean signatureRequired = integrationSecret != null || triggers.stream().anyMatch(trigger -> secretForTrigger(trigger, integrationSecret) != null);
        int executions = 0;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (TriggerDefinition trigger : triggers) {
            if (!"ACTIVE".equalsIgnoreCase(trigger.workflowStatus())) {
                continue;
            }
            String secret = secretForTrigger(trigger, integrationSecret);
            if (secret != null && !isValidSignature(signature, secret, payloadJson)) {
                continue;
            }
            if (!matchesWebhookEventType(trigger.eventType(), trigger.config(), eventType) || !matchesPayloadFilter(trigger.config(), payload)) {
                continue;
            }
            touchTrigger(trigger.id(), now);
            startWorkflowExecution(trigger.workflowId(), "WEBHOOK", provider + (eventType == null ? " webhook" : " webhook: " + eventType), payload == null ? Map.of() : payload, correlationId);
            executions++;
        }

        return new WebhookReceipt(provider, eventType, triggers.size(), executions, !signatureRequired || executions > 0, now);
    }

    private void executeWorkflow(
        String executionId,
        WorkflowDefinition workflow,
        String triggerType,
        String triggerLabel,
        Map<String, Object> payload,
        String correlationId,
        OffsetDateTime startedAt
    ) {
        List<Map<String, Object>> stepStates = new ArrayList<>();
        for (int index = 0; index < workflow.steps().size(); index++) {
            stepStates.add(stepState(index, workflow.steps().get(index), "PENDING", null, null, null));
        }

        try {
            updateExecution(executionId, "RUNNING", null, 0, buildResultPayload(triggerType, triggerLabel, correlationId, payload, stepStates, -1));
            for (int index = 0; index < workflow.steps().size(); index++) {
                OffsetDateTime stepStartedAt = OffsetDateTime.now(ZoneOffset.UTC);
                stepStates.set(index, stepState(index, workflow.steps().get(index), "RUNNING", stepStartedAt, null, "Running"));
                updateExecution(executionId, "RUNNING", null, 0, buildResultPayload(triggerType, triggerLabel, correlationId, payload, stepStates, index));
                Thread.sleep(200L);
                OffsetDateTime stepCompletedAt = OffsetDateTime.now(ZoneOffset.UTC);
                stepStates.set(index, stepState(index, workflow.steps().get(index), "COMPLETED", stepStartedAt, stepCompletedAt, "Completed"));
                updateExecution(executionId, "RUNNING", null, 0, buildResultPayload(triggerType, triggerLabel, correlationId, payload, stepStates, index));
            }

            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            int durationMs = (int) Math.max(1L, Duration.between(startedAt, completedAt).toMillis());
            jdbcTemplate.update(
                "UPDATE aieap.workflow_executions SET status = 'COMPLETED', completed_at = ?, duration_ms = ?, error_message = NULL, result_json = ?::jsonb WHERE id = ?::uuid",
                completedAt,
                durationMs,
                toJson(buildResultPayload(triggerType, triggerLabel, correlationId, payload, stepStates, workflow.steps().isEmpty() ? -1 : workflow.steps().size() - 1)),
                executionId
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            markExecutionFailed(executionId, triggerType, triggerLabel, payload, correlationId, stepStates, startedAt, "Workflow execution interrupted");
        } catch (Exception exception) {
            log.error("Workflow execution {} failed", executionId, exception);
            markExecutionFailed(executionId, triggerType, triggerLabel, payload, correlationId, stepStates, startedAt, exception.getMessage());
        }
    }

    private void markExecutionFailed(
        String executionId,
        String triggerType,
        String triggerLabel,
        Map<String, Object> payload,
        String correlationId,
        List<Map<String, Object>> stepStates,
        OffsetDateTime startedAt,
        String message
    ) {
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int durationMs = (int) Math.max(1L, Duration.between(startedAt, completedAt).toMillis());
        jdbcTemplate.update(
            "UPDATE aieap.workflow_executions SET status = 'FAILED', completed_at = ?, duration_ms = ?, error_message = ?, result_json = ?::jsonb WHERE id = ?::uuid",
            completedAt,
            durationMs,
            message,
            toJson(buildResultPayload(triggerType, triggerLabel, correlationId, payload, stepStates, currentStepIndex(stepStates))),
            executionId
        );
    }

    private void updateExecution(String executionId, String status, String errorMessage, int durationMs, Map<String, Object> result) {
        jdbcTemplate.update(
            "UPDATE aieap.workflow_executions SET status = ?, error_message = ?, duration_ms = ?, result_json = ?::jsonb WHERE id = ?::uuid",
            status,
            errorMessage,
            durationMs,
            toJson(result),
            executionId
        );
    }

    private boolean isDue(TriggerDefinition trigger, OffsetDateTime now) {
        if (trigger.scheduleCron() == null || trigger.scheduleCron().isBlank()) {
            return false;
        }
        try {
            CronExpression cronExpression = CronExpression.parse(trigger.scheduleCron());
            OffsetDateTime reference = trigger.lastFiredAt() != null ? trigger.lastFiredAt() : trigger.createdAt().minusSeconds(1);
            ZonedDateTime next = cronExpression.next(reference.toZonedDateTime());
            return next != null && !next.isAfter(now.toZonedDateTime());
        } catch (IllegalArgumentException exception) {
            log.warn("Skipping invalid cron expression {} for trigger {}", trigger.scheduleCron(), trigger.id());
            return false;
        }
    }

    private boolean matchesSource(Map<String, Object> config, String source) {
        Object configuredSource = config.get("source");
        if (!(configuredSource instanceof String configuredValue) || configuredValue.isBlank()) {
            return true;
        }
        return configuredValue.equalsIgnoreCase(source == null ? "" : source);
    }

    private boolean matchesPayloadFilter(Map<String, Object> config, Map<String, Object> payload) {
        Object filter = config.get("payloadFilter");
        if (!(filter instanceof Map<?, ?> filterMap)) {
            return true;
        }
        if (payload == null) {
            return false;
        }
        for (Map.Entry<?, ?> entry : filterMap.entrySet()) {
            Object payloadValue = payload.get(String.valueOf(entry.getKey()));
            if (!String.valueOf(entry.getValue()).equals(String.valueOf(payloadValue))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesWebhookEventType(String triggerEventType, Map<String, Object> config, String payloadEventType) {
        String expected = triggerEventType;
        if ((expected == null || expected.isBlank()) && config.get("eventType") instanceof String configuredEventType) {
            expected = configuredEventType;
        }
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equalsIgnoreCase(payloadEventType == null ? "" : payloadEventType);
    }

    private String extractEventType(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (String key : List.of("eventType", "type", "event", "action")) {
            Object value = payload.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private String loadConnectedIntegrationSecret(String provider) {
        List<String> rows = jdbcTemplate.query(
            "SELECT config_json::text AS config_json FROM aieap.integrations WHERE LOWER(provider) = LOWER(?) AND status = 'CONNECTED' ORDER BY updated_at DESC LIMIT 1",
            (rs, rowNum) -> rs.getString("config_json"),
            provider
        );
        if (rows.isEmpty()) {
            return null;
        }
        Object secret = readJsonMap(rows.getFirst()).get("webhookSecret");
        return secret == null ? null : String.valueOf(secret);
    }

    private String secretForTrigger(TriggerDefinition trigger, String integrationSecret) {
        Object triggerSecret = trigger.config().get("secret");
        if (triggerSecret instanceof String secret && !secret.isBlank()) {
            return secret;
        }
        return integrationSecret == null || integrationSecret.isBlank() ? null : integrationSecret;
    }

    private String findSignatureHeader(Map<String, String> headers) {
        for (String key : headers.keySet()) {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (normalized.equals("x-webhook-signature") || normalized.equals("x-hub-signature-256") || normalized.equals("x-signature")) {
                return headers.get(key);
            }
        }
        return null;
    }

    private boolean isValidSignature(String signature, String secret, String payloadJson) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            String expectedHex = toHex(digest);
            String expectedBase64 = Base64.getEncoder().encodeToString(digest);
            String normalizedSignature = signature.startsWith("sha256=") ? signature.substring("sha256=".length()) : signature;
            return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8), normalizedSignature.getBytes(StandardCharsets.UTF_8))
                || MessageDigest.isEqual(expectedBase64.getBytes(StandardCharsets.UTF_8), normalizedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            log.warn("Unable to validate webhook signature: {}", exception.getMessage());
            return false;
        }
    }

    private void touchTrigger(String triggerId, OffsetDateTime now) {
        jdbcTemplate.update(
            "UPDATE aieap.workflow_triggers SET last_fired_at = ?, updated_at = ? WHERE id = ?::uuid",
            now,
            now,
            triggerId
        );
    }

    private WorkflowDefinition loadWorkflow(String workflowId) {
        List<WorkflowDefinition> rows = jdbcTemplate.query(
            "SELECT id::text AS id, name, status FROM aieap.workflows WHERE id = ?::uuid",
            (rs, rowNum) -> new WorkflowDefinition(rs.getString("id"), rs.getString("name"), rs.getString("status"), loadWorkflowSteps(rs.getString("id"))),
            workflowId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        return rows.getFirst();
    }

    private List<TriggerDefinition> loadTriggers(String sql, Object... args) {
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new TriggerDefinition(
                rs.getString("id"),
                rs.getString("workflow_id"),
                rs.getString("workflow_name"),
                rs.getString("workflow_status"),
                rs.getString("trigger_type"),
                rs.getString("schedule_cron"),
                rs.getString("event_type"),
                readJsonMap(rs.getString("config_json")),
                rs.getObject("last_fired_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
            ),
            args
        );
    }

    private List<String> loadWorkflowSteps(String workflowId) {
        return jdbcTemplate.query(
            "SELECT step_name FROM aieap.workflow_steps WHERE workflow_id = ?::uuid ORDER BY step_index",
            (rs, rowNum) -> rs.getString("step_name"),
            workflowId
        );
    }

    private Map<String, Object> buildResultPayload(String triggerType, String triggerLabel, String correlationId, Map<String, Object> payload, List<Map<String, Object>> stepStates, int currentStepIndex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggerType", triggerType);
        result.put("triggerLabel", triggerLabel);
        result.put("correlationId", correlationId);
        result.put("payload", payload == null ? Map.of() : payload);
        result.put("currentStepIndex", currentStepIndex);
        result.put("stepResults", stepStates);
        int completedSteps = 0;
        for (Map<String, Object> stepState : stepStates) {
            if ("COMPLETED".equals(stepState.get("status"))) {
                completedSteps++;
            }
        }
        result.put("progressPercent", stepStates.isEmpty() ? 100 : (completedSteps * 100) / stepStates.size());
        return result;
    }

    private List<WorkflowStepResult> extractStepResults(Map<String, Object> resultJson) {
        Object rawStepResults = resultJson.get("stepResults");
        if (!(rawStepResults instanceof List<?> stepResults)) {
            return List.of();
        }

        List<WorkflowStepResult> mapped = new ArrayList<>();
        for (Object stepResult : stepResults) {
            if (!(stepResult instanceof Map<?, ?> rawMap)) {
                continue;
            }
            mapped.add(new WorkflowStepResult(
                asInt(rawMap.get("index")),
                mapString(rawMap, "name", "Unnamed step"),
                mapString(rawMap, "status", "PENDING"),
                parseTimestamp(rawMap.get("startedAt")),
                parseTimestamp(rawMap.get("completedAt")),
                rawMap.get("message") == null ? null : String.valueOf(rawMap.get("message"))
            ));
        }
        return mapped;
    }

    private OffsetDateTime parseTimestamp(Object rawValue) {
        if (!(rawValue instanceof String value) || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
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

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode workflow metadata", exception);
        }
    }

    private Map<String, Object> stepState(int index, String name, String status, OffsetDateTime startedAt, OffsetDateTime completedAt, String message) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("index", index);
        step.put("name", name);
        step.put("status", status);
        step.put("startedAt", startedAt == null ? null : startedAt.toString());
        step.put("completedAt", completedAt == null ? null : completedAt.toString());
        step.put("message", message);
        return step;
    }

    private int currentStepIndex(List<Map<String, Object>> stepStates) {
        for (int index = stepStates.size() - 1; index >= 0; index--) {
            if (!"PENDING".equals(String.valueOf(stepStates.get(index).get("status")))) {
                return index;
            }
        }
        return -1;
    }

    private WorkflowExecutionItem mapExecution(String executionId, String status, String triggerType, String triggerLabel, OffsetDateTime startedAt, OffsetDateTime completedAt, int durationMs, String errorMessage, List<?> stepStates) {
        List<WorkflowStepResult> steps = new ArrayList<>();
        for (Object stepState : stepStates) {
            if (stepState instanceof WorkflowStepResult workflowStepResult) {
                steps.add(workflowStepResult);
            } else if (stepState instanceof Map<?, ?> rawMap) {
                steps.add(new WorkflowStepResult(
                    asInt(rawMap.get("index")),
                    mapString(rawMap, "name", "Unnamed step"),
                    mapString(rawMap, "status", "PENDING"),
                    parseTimestamp(rawMap.get("startedAt")),
                    parseTimestamp(rawMap.get("completedAt")),
                    rawMap.get("message") == null ? null : String.valueOf(rawMap.get("message"))
                ));
            }
        }
        return new WorkflowExecutionItem(executionId, status, triggerType, triggerLabel, startedAt, completedAt, durationMs, errorMessage, steps);
    }

    private String mapString(Map<?, ?> rawMap, String key, String defaultValue) {
        Object value = rawMap.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte current : value) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }

    private record WorkflowDefinition(String id, String name, String status, List<String> steps) {
    }

    private record TriggerDefinition(
        String id,
        String workflowId,
        String workflowName,
        String workflowStatus,
        String triggerType,
        String scheduleCron,
        String eventType,
        Map<String, Object> config,
        OffsetDateTime lastFiredAt,
        OffsetDateTime createdAt
    ) {
    }
}