package com.aieap.platform.task;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.AllowedValues;
import com.aieap.platform.common.validation.NullOrNotBlank;
import com.aieap.platform.common.validation.NullOrValidUuid;
import com.aieap.platform.task.kafka.KafkaEventPublisher;
import com.aieap.platform.task.kafka.events.TaskCreatedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Tasks")
@RequestMapping("/tasks")
@PreAuthorize("isAuthenticated()")
public class TaskController {
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    public TaskController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<TaskItem>> listTasks(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) @AllowedValues(values = {"PENDING", "IN_PROGRESS", "COMPLETED"}) String status,
        @RequestParam(required = false) @AllowedValues(values = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}) String priority,
        @RequestParam(defaultValue = "dueAt") @AllowedValues(values = {"dueAt", "priority", "createdAt"}, allowNull = false) String sort,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        String userId = currentUserId(authentication);
        boolean admin = isAdmin(authentication);
        List<TaskItem> filtered = loadTasks(userId, admin).stream()
            .filter(task -> status == null || task.status().equalsIgnoreCase(status))
            .filter(task -> priority == null || task.priority().equalsIgnoreCase(priority))
            .sorted(resolveComparator(sort))
            .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<TaskItem> pageItems = new java.util.ArrayList<>(filtered.subList(fromIndex, toIndex));

        return ResponseFactory.success(request, new PageEnvelope<>(pageItems, page, size, filtered.size(), sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @CacheEvict(value = "tasks", allEntries = true)
    public ApiEnvelope<TaskItem> createTask(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateTaskRequest createTaskRequest,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String userId = currentUserId(authentication);
        boolean admin = isAdmin(authentication);
        String id = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        String priority = createTaskRequest.priority() == null || createTaskRequest.priority().isBlank()
            ? "MEDIUM"
            : createTaskRequest.priority().toUpperCase();
        OffsetDateTime dueAt = createTaskRequest.dueAt();
        String source = createTaskRequest.source() == null ? "manual" : createTaskRequest.source();
        String metadataJson = toJson(Map.of("source", source));

        String requestedAssignee = parseUuidOrNull(createTaskRequest.assigneeUserId());
        if (!admin && requestedAssignee != null && !requestedAssignee.equals(userId)) {
            throw new AccessDeniedException("Employees can only create tasks for themselves");
        }

        db.update(
            "INSERT INTO aieap.tasks (id, source_email_id, title, description, assignee_user_id, created_by_user_id, priority, status, due_at, metadata_json, created_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?::uuid, ?, 'PENDING', ?, ?::jsonb, NOW(), NOW()) " +
            "ON CONFLICT (id) DO NOTHING",
            id,
            parseUuidOrNull(source),
            InputSanitizer.requiredText(createTaskRequest.title()),
            InputSanitizer.nullableText(createTaskRequest.description()),
            requestedAssignee == null ? userId : requestedAssignee,
            userId,
            priority,
            dueAt,
            metadataJson
        );
        TaskItem task = task(id, userId, admin);
        publishTaskCreatedEvent(task, request.getHeader("X-Correlation-ID"));
        createTaskNotificationFallback(task, userId, request.getHeader("X-Correlation-ID"));
        return ResponseFactory.success(request, task);
    }

    @PatchMapping("/{id}")
    @Transactional
    @CacheEvict(value = "tasks", allEntries = true)
    public ApiEnvelope<TaskItem> updateTask(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTaskRequest updateTaskRequest,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        String taskId = id.toString();
        String userId = currentUserId(authentication);
        boolean admin = isAdmin(authentication);
        TaskItem existing = task(taskId, userId, admin);
        if (!admin && updateTaskRequest.assigneeUserId() != null && !updateTaskRequest.assigneeUserId().equals(userId)) {
            throw new AccessDeniedException("Employees can only assign tasks to themselves");
        }
        String updatedPriority = updateTaskRequest.priority() == null
            ? existing.priority()
            : updateTaskRequest.priority().toUpperCase();
        String updatedStatus = updateTaskRequest.status() == null
            ? existing.status()
            : updateTaskRequest.status().toUpperCase();
        TaskItem updated = new TaskItem(
            existing.id(),
            updateTaskRequest.title() == null ? existing.title() : InputSanitizer.requiredText(updateTaskRequest.title()),
            updateTaskRequest.description() == null ? existing.description() : InputSanitizer.nullableText(updateTaskRequest.description()),
            updateTaskRequest.assigneeUserId() == null ? existing.assigneeUserId() : updateTaskRequest.assigneeUserId(),
            updatedPriority,
            updatedStatus,
            updateTaskRequest.dueAt() == null ? existing.dueAt() : updateTaskRequest.dueAt(),
            OffsetDateTime.now(),
            existing.source()
        );
        JdbcTemplate db = requireJdbc();
        db.update(
            "UPDATE aieap.tasks SET title = ?, description = ?, assignee_user_id = ?::uuid, priority = ?, status = ?, due_at = ?, metadata_json = ?::jsonb, updated_at = NOW() WHERE id = ?::uuid",
            updated.title(),
            updated.description(),
            parseUuidOrNull(updated.assigneeUserId()),
            updated.priority(),
            updated.status(),
            updated.dueAt(),
            toJson(Map.of("source", updated.source())),
            taskId
        );
        return ResponseFactory.success(request, updated);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @CacheEvict(value = "tasks", allEntries = true)
    public ApiEnvelope<Map<String, String>> deleteTask(@PathVariable UUID id, JwtAuthenticationToken authentication, HttpServletRequest request) {
        String taskId = id.toString();
        String userId = currentUserId(authentication);
        JdbcTemplate db = requireJdbc();
        if (isAdmin(authentication)) {
            db.update("DELETE FROM aieap.tasks WHERE id = ?::uuid", taskId);
        } else {
            db.update("DELETE FROM aieap.tasks WHERE id = ?::uuid AND (created_by_user_id = ?::uuid OR assignee_user_id = ?::uuid)", taskId, userId, userId);
        }
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", taskId));
    }

    @GetMapping("/board")
    public ApiEnvelope<TaskBoardResponse> board(JwtAuthenticationToken authentication, HttpServletRequest request) {
        List<TaskItem> items = loadTasks(currentUserId(authentication), isAdmin(authentication));
        List<BoardColumn> columns = List.of(
            new BoardColumn("PENDING", new java.util.ArrayList<>(items.stream().filter(task -> "PENDING".equals(task.status())).toList())),
            new BoardColumn("IN_PROGRESS", new java.util.ArrayList<>(items.stream().filter(task -> "IN_PROGRESS".equals(task.status())).toList())),
            new BoardColumn("COMPLETED", new java.util.ArrayList<>(items.stream().filter(task -> "COMPLETED".equals(task.status())).toList()))
        );
        return ResponseFactory.success(request, new TaskBoardResponse(columns));
    }

    private TaskItem task(String id, String userId) {
        return task(id, userId, false);
    }

    private TaskItem task(String id, String userId, boolean admin) {
        JdbcTemplate db = requireJdbc();
        String sql = "SELECT id::text AS id, title, description, assignee_user_id::text AS assignee_user_id, priority, status, due_at, updated_at, metadata_json::text AS metadata_json " +
            "FROM aieap.tasks WHERE id = ?::uuid" +
            (admin ? "" : " AND (created_by_user_id = ?::uuid OR assignee_user_id = ?::uuid)");
        Object[] args = admin ? new Object[] { id } : new Object[] { id, userId, userId };
        List<TaskItem> rows = db.query(
            sql,
            (rs, rowNum) -> mapTask(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("assignee_user_id"),
                rs.getString("priority"),
                rs.getString("status"),
                readOffsetDateTime(rs, "due_at"),
                readOffsetDateTime(rs, "updated_at"),
                rs.getString("metadata_json")
            ),
            args
        );
        TaskItem task = rows.isEmpty() ? null : rows.getFirst();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private List<TaskItem> loadTasks(String userId, boolean admin) {
        JdbcTemplate db = requireJdbc();
        String sql = "SELECT id::text AS id, title, description, assignee_user_id::text AS assignee_user_id, priority, status, due_at, updated_at, metadata_json::text AS metadata_json " +
            "FROM aieap.tasks" +
            (admin ? "" : " WHERE created_by_user_id = ?::uuid OR assignee_user_id = ?::uuid");
        Object[] args = admin ? new Object[] { } : new Object[] { userId, userId };
        return db.query(
            sql,
            (rs, rowNum) -> mapTask(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("assignee_user_id"),
                rs.getString("priority"),
                rs.getString("status"),
                readOffsetDateTime(rs, "due_at"),
                readOffsetDateTime(rs, "updated_at"),
                rs.getString("metadata_json")
            ),
            args
        );
    }

    private String currentUserId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        String userId = authentication.getToken().getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        return userId;
    }

    private boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private TaskItem mapTask(
        String id,
        String title,
        String description,
        String assigneeUserId,
        String priority,
        String status,
        OffsetDateTime dueAt,
        OffsetDateTime updatedAt,
        String metadataJson
    ) {
        String source = "manual";
        try {
            Map<String, String> metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, String>>() {});
            if (metadata != null && metadata.get("source") != null) {
                source = metadata.get("source");
            }
        } catch (Exception ignored) {
            source = "manual";
        }
        return new TaskItem(id, title, description, assigneeUserId, priority, status, dueAt, updatedAt, source);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private String parseUuidOrNull(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(input).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String toJson(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode task metadata", ex);
        }
    }

    private OffsetDateTime readOffsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return rs.getTimestamp(column).toInstant().atOffset(ZoneOffset.UTC);
    }

    private Comparator<TaskItem> resolveComparator(String sort) {
        return switch (sort) {
            case "priority" -> Comparator.comparing(TaskItem::priority);
            case "createdAt" -> Comparator.comparing(TaskItem::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            default -> Comparator.comparing(TaskItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private void publishTaskCreatedEvent(TaskItem task, String incomingCorrelationId) {
        if (kafkaEventPublisher == null) {
            return;
        }
        String correlationId = (incomingCorrelationId != null && !incomingCorrelationId.isBlank())
            ? incomingCorrelationId
            : UUID.randomUUID().toString();
        kafkaEventPublisher.publish(
            "task.created",
            task.id(),
            new TaskCreatedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                task.id(),
                task.title(),
                task.description(),
                task.assigneeUserId(),
                task.priority(),
                task.status(),
                task.source(),
                task.assigneeUserId(),
                task.dueAt(),
                java.time.OffsetDateTime.now().toString()
            ),
            correlationId
        );
    }

    private void createTaskNotificationFallback(TaskItem task, String actorUserId, String incomingCorrelationId) {
        if (jdbcTemplate == null || task == null || task.assigneeUserId() == null || task.assigneeUserId().isBlank()) {
            return;
        }

        String correlationId = (incomingCorrelationId != null && !incomingCorrelationId.isBlank())
            ? incomingCorrelationId
            : UUID.randomUUID().toString();
        String message = "Priority: " + task.priority() + " | Source: " + task.source()
            + (task.dueAt() != null ? " | Due: " + task.dueAt() : "");

        jdbcTemplate.update(
            "INSERT INTO aieap.notifications (id, user_id, channel, notification_type, title, message, status, metadata_json, created_at) " +
            "VALUES (?::uuid, ?::uuid, 'IN_APP', 'TASK_CREATED', ?, ?, 'UNREAD', ?::jsonb, NOW())",
            UUID.randomUUID().toString(),
            task.assigneeUserId(),
            "New task: " + task.title(),
            message,
            toJson(Map.of("correlationId", correlationId, "fallback", "true", "actorUserId", actorUserId))
        );
    }

    public record TaskItem(String id, String title, String description, String assigneeUserId, String priority, String status, OffsetDateTime dueAt, OffsetDateTime updatedAt, String source) {
    }

    public record CreateTaskRequest(
        @NotBlank String title,
        @NullOrNotBlank String description,
        @NullOrValidUuid String assigneeUserId,
        @AllowedValues(values = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}) String priority,
        OffsetDateTime dueAt,
        @NullOrNotBlank String source
    ) {
    }

    public record UpdateTaskRequest(
        @NullOrNotBlank String title,
        @NullOrNotBlank String description,
        @NullOrValidUuid String assigneeUserId,
        @AllowedValues(values = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}) String priority,
        @AllowedValues(values = {"PENDING", "IN_PROGRESS", "COMPLETED"}) String status,
        OffsetDateTime dueAt
    ) {
    }

    public record BoardColumn(String status, List<TaskItem> tasks) {
    }

    public record TaskBoardResponse(List<BoardColumn> columns) {
    }
}
