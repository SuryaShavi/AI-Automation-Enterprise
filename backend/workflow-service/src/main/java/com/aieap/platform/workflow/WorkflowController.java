package com.aieap.platform.workflow;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public WorkflowController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        String id = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        List<String> steps = createWorkflowRequest.steps() == null ? List.of() : createWorkflowRequest.steps();
        db.update(
            "INSERT INTO aieap.workflows (id, name, status, created_at, updated_at) VALUES (?::uuid, ?, 'DRAFT', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            id,
            createWorkflowRequest.name()
        );
        int idx = 0;
        for (String step : steps) {
            db.update(
                "INSERT INTO aieap.workflow_steps (id, workflow_id, step_index, step_name, created_at) VALUES (?::uuid, ?::uuid, ?, ?, NOW())",
                UUID.randomUUID().toString(),
                id,
                idx++,
                step
            );
        }
        return ResponseFactory.success(request, workflow(id));
    }

    @PatchMapping("/workflows/{id}/status")
    @Transactional
    public ApiEnvelope<WorkflowItem> patchStatus(@PathVariable String id, @Valid @RequestBody WorkflowStatusRequest workflowStatusRequest, HttpServletRequest request) {
        String nextStatus = workflowStatusRequest.status().toUpperCase();
        if (!"ACTIVE".equals(nextStatus) && !"PAUSED".equals(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or PAUSED");
        }
        JdbcTemplate db = requireJdbc();
        int updated = db.update("UPDATE aieap.workflows SET status = ?, updated_at = NOW() WHERE id = ?::uuid", nextStatus, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        return ResponseFactory.success(request, workflow(id));
    }

    @DeleteMapping("/workflows/{id}")
    @Transactional
    public ApiEnvelope<Map<String, String>> delete(@PathVariable String id, HttpServletRequest request) {
        workflow(id);
        JdbcTemplate db = requireJdbc();
        db.update("DELETE FROM aieap.workflows WHERE id = ?::uuid", id);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", id));
    }

    @PostMapping("/workflows/{id}/run")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<Map<String, Object>> run(@PathVariable String id, HttpServletRequest request) {
        workflow(id);
        JdbcTemplate db = requireJdbc();
        String executionId = UUID.randomUUID().toString();
        db.update(
            "INSERT INTO aieap.workflow_executions (id, workflow_id, status, started_at, duration_ms, result_json) " +
            "VALUES (?::uuid, ?::uuid, 'RUNNING', NOW(), 0, '{}'::jsonb)",
            executionId, id
        );
        // Simulate synchronous execution: mark COMPLETED immediately.
        db.update(
            "UPDATE aieap.workflow_executions SET status = 'COMPLETED', completed_at = NOW(), " +
            "duration_ms = EXTRACT(MILLISECONDS FROM (NOW() - started_at))::INT, " +
            "result_json = '{\"outcome\":\"success\"}'::jsonb WHERE id = ?::uuid",
            executionId
        );
        Map<String, Object> result = db.queryForMap(
            "SELECT id::text AS id, status, started_at, completed_at, duration_ms FROM aieap.workflow_executions WHERE id = ?::uuid",
            executionId
        );
        return ResponseFactory.success(request, Map.of(
            "executionId", result.get("id"),
            "status", result.get("status"),
            "startedAt", String.valueOf(result.get("started_at")),
            "completedAt", String.valueOf(result.get("completed_at")),
            "durationMs", result.get("duration_ms")
        ));
    }

    @GetMapping("/workflows/{id}/executions")
    public ApiEnvelope<List<Map<String, Object>>> executions(@PathVariable String id, HttpServletRequest request) {
        workflow(id);
        JdbcTemplate db = requireJdbc();
        List<Map<String, Object>> rows = db.query(
            "SELECT id::text AS id, status, started_at, completed_at, duration_ms FROM aieap.workflow_executions WHERE workflow_id = ?::uuid ORDER BY started_at DESC",
            (rs, rowNum) -> {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("executionId", rs.getString("id"));
                row.put("status", rs.getString("status"));
                row.put("startedAt", String.valueOf(rs.getObject("started_at", OffsetDateTime.class)));
                row.put("completedAt", String.valueOf(rs.getObject("completed_at", OffsetDateTime.class)));
                row.put("durationMs", rs.getInt("duration_ms"));
                return row;
            },
            id
        );
        return ResponseFactory.success(request, rows);
    }

    @GetMapping("/integrations")
    public ApiEnvelope<List<IntegrationItem>> listIntegrations(HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        String userId = defaultUserId();
        List<IntegrationItem> items = db.query(
            "SELECT provider, status, auth_type, connected_at FROM aieap.integrations WHERE user_id = ?::uuid ORDER BY provider",
            (rs, rowNum) -> new IntegrationItem(
                rs.getString("provider"),
                rs.getString("status"),
                rs.getString("auth_type"),
                rs.getObject("connected_at", OffsetDateTime.class)
            ),
            userId
        );
        return ResponseFactory.success(request, items);
    }

    @PostMapping("/integrations/{provider}/connect")
    @Transactional
    public ApiEnvelope<IntegrationItem> connect(@PathVariable String provider, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        String userId = defaultUserId();
        String authType = payload == null ? "OAuth2" : String.valueOf(payload.getOrDefault("authType", "OAuth2"));
        db.update(
            "INSERT INTO aieap.integrations (id, user_id, provider, status, auth_type, config_json, connected_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, 'CONNECTED', ?, ?::jsonb, NOW(), NOW()) " +
            "ON CONFLICT (user_id, provider) DO UPDATE SET status = 'CONNECTED', auth_type = EXCLUDED.auth_type, config_json = EXCLUDED.config_json, connected_at = NOW(), disconnected_at = NULL, updated_at = NOW()",
            UUID.randomUUID().toString(),
            userId,
            provider,
            authType,
            toJson(payload == null ? Map.of() : payload)
        );
        IntegrationItem updated = integration(userId, provider);
        return ResponseFactory.success(request, updated);
    }

    @PostMapping("/integrations/{provider}/disconnect")
    @Transactional
    public ApiEnvelope<IntegrationItem> disconnect(@PathVariable String provider, HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        String userId = defaultUserId();
        db.update(
            "UPDATE aieap.integrations SET status = 'DISCONNECTED', disconnected_at = NOW(), connected_at = NULL, updated_at = NOW() WHERE user_id = ?::uuid AND provider = ?",
            userId,
            provider
        );
        IntegrationItem updated = integration(userId, provider);
        return ResponseFactory.success(request, updated);
    }

    @PostMapping("/integrations/webhooks/{provider}")
    public ApiEnvelope<Map<String, String>> webhook(@PathVariable String provider, @RequestBody(required = false) Map<String, Object> payload, HttpServletRequest request) {
        return ResponseFactory.success(request, Map.of("provider", provider, "status", "accepted", "received", String.valueOf(payload != null)));
    }

    private WorkflowItem workflow(String id) {
        JdbcTemplate db = requireJdbc();
        List<WorkflowItem> rows = db.query(
            "SELECT id::text AS id, name, status, created_at FROM aieap.workflows WHERE id = ?::uuid",
            (rs, rowNum) -> new WorkflowItem(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("status"),
                loadWorkflowSteps(rs.getString("id")),
                rs.getObject("created_at", OffsetDateTime.class)
            ),
            id
        );
        WorkflowItem workflow = rows.isEmpty() ? null : rows.getFirst();
        if (workflow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        }
        return workflow;
    }

    private List<WorkflowItem> loadWorkflows() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, name, status, created_at FROM aieap.workflows ORDER BY created_at DESC",
            (rs, rowNum) -> new WorkflowItem(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("status"),
                loadWorkflowSteps(rs.getString("id")),
                rs.getObject("created_at", OffsetDateTime.class)
            )
        );
    }

    private List<String> loadWorkflowSteps(String workflowId) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT step_name FROM aieap.workflow_steps WHERE workflow_id = ?::uuid ORDER BY step_index",
            (rs, rowNum) -> rs.getString("step_name"),
            workflowId
        );
    }

    private IntegrationItem integration(String userId, String provider) {
        JdbcTemplate db = requireJdbc();
        List<IntegrationItem> rows = db.query(
            "SELECT provider, status, auth_type, connected_at FROM aieap.integrations WHERE user_id = ?::uuid AND provider = ?",
            (rs, rowNum) -> new IntegrationItem(
                rs.getString("provider"),
                rs.getString("status"),
                rs.getString("auth_type"),
                rs.getObject("connected_at", OffsetDateTime.class)
            ),
            userId,
            provider
        );
        if (rows.isEmpty()) {
            return new IntegrationItem(provider, "DISCONNECTED", "OAuth2", null);
        }
        return rows.getFirst();
    }

    private String defaultUserId() {
        JdbcTemplate db = requireJdbc();
        String userId = db.query(
            "SELECT id::text AS id FROM aieap.users WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
            rs -> rs.next() ? rs.getString("id") : null
        );
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active user available for workflow integrations");
        }
        return userId;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode integration payload", ex);
        }
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
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