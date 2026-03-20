package com.aieap.platform.task;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Comparator;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Tasks")
@RequestMapping("/tasks")
public class TaskController {
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public TaskController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<TaskItem>> listTasks(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String priority,
        @RequestParam(defaultValue = "dueAt") String sort,
        HttpServletRequest request
    ) {
        List<TaskItem> filtered = loadTasks().stream()
            .filter(task -> status == null || task.status().equalsIgnoreCase(status))
            .filter(task -> priority == null || task.priority().equalsIgnoreCase(priority))
            .sorted(resolveComparator(sort))
            .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());

        return ResponseFactory.success(request, new PageEnvelope<>(filtered.subList(fromIndex, toIndex), page, size, filtered.size(), sort));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<TaskItem> createTask(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateTaskRequest createTaskRequest,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String id = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        String priority = createTaskRequest.priority() == null || createTaskRequest.priority().isBlank()
            ? "MEDIUM"
            : createTaskRequest.priority().toUpperCase();
        OffsetDateTime dueAt = createTaskRequest.dueAt();
        String source = createTaskRequest.source() == null ? "manual" : createTaskRequest.source();
        String metadataJson = toJson(Map.of("source", source));

        db.update(
            "INSERT INTO aieap.tasks (id, source_email_id, title, description, assignee_user_id, priority, status, due_at, metadata_json, created_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?, 'PENDING', ?, ?::jsonb, NOW(), NOW()) " +
            "ON CONFLICT (id) DO NOTHING",
            id,
            parseUuidOrNull(source),
            createTaskRequest.title(),
            createTaskRequest.description(),
            parseUuidOrNull(createTaskRequest.assigneeUserId()),
            priority,
            dueAt,
            metadataJson
        );
        TaskItem task = task(id);
        return ResponseFactory.success(request, task);
    }

    @PatchMapping("/{id}")
    @Transactional
    public ApiEnvelope<TaskItem> updateTask(
        @PathVariable String id,
        @Valid @RequestBody UpdateTaskRequest updateTaskRequest,
        HttpServletRequest request
    ) {
        TaskItem existing = task(id);
        TaskItem updated = new TaskItem(
            existing.id(),
            updateTaskRequest.title() == null ? existing.title() : updateTaskRequest.title(),
            updateTaskRequest.description() == null ? existing.description() : updateTaskRequest.description(),
            updateTaskRequest.assigneeUserId() == null ? existing.assigneeUserId() : updateTaskRequest.assigneeUserId(),
            updateTaskRequest.priority() == null ? existing.priority() : updateTaskRequest.priority(),
            updateTaskRequest.status() == null ? existing.status() : updateTaskRequest.status(),
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
            id
        );
        return ResponseFactory.success(request, updated);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiEnvelope<Map<String, String>> deleteTask(@PathVariable String id, HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        db.update("DELETE FROM aieap.tasks WHERE id = ?::uuid", id);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", id));
    }

    @GetMapping("/board")
    public ApiEnvelope<TaskBoardResponse> board(HttpServletRequest request) {
        List<TaskItem> items = loadTasks();
        List<BoardColumn> columns = List.of(
            new BoardColumn("PENDING", items.stream().filter(task -> "PENDING".equals(task.status())).toList()),
            new BoardColumn("IN_PROGRESS", items.stream().filter(task -> "IN_PROGRESS".equals(task.status())).toList()),
            new BoardColumn("COMPLETED", items.stream().filter(task -> "COMPLETED".equals(task.status())).toList())
        );
        return ResponseFactory.success(request, new TaskBoardResponse(columns));
    }

    private TaskItem task(String id) {
        JdbcTemplate db = requireJdbc();
        List<TaskItem> rows = db.query(
            "SELECT id::text AS id, title, description, assignee_user_id::text AS assignee_user_id, priority, status, due_at, updated_at, metadata_json::text AS metadata_json " +
            "FROM aieap.tasks WHERE id = ?::uuid",
            (rs, rowNum) -> mapTask(rs.getString("id"), rs.getString("title"), rs.getString("description"), rs.getString("assignee_user_id"), rs.getString("priority"), rs.getString("status"), rs.getObject("due_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), rs.getString("metadata_json")),
            id
        );
        TaskItem task = rows.isEmpty() ? null : rows.getFirst();
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private List<TaskItem> loadTasks() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, title, description, assignee_user_id::text AS assignee_user_id, priority, status, due_at, updated_at, metadata_json::text AS metadata_json FROM aieap.tasks",
            (rs, rowNum) -> mapTask(rs.getString("id"), rs.getString("title"), rs.getString("description"), rs.getString("assignee_user_id"), rs.getString("priority"), rs.getString("status"), rs.getObject("due_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), rs.getString("metadata_json"))
        );
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

    private Comparator<TaskItem> resolveComparator(String sort) {
        return switch (sort) {
            case "priority" -> Comparator.comparing(TaskItem::priority);
            case "createdAt" -> Comparator.comparing(TaskItem::updatedAt).reversed();
            default -> Comparator.comparing(TaskItem::dueAt);
        };
    }

    public record TaskItem(String id, String title, String description, String assigneeUserId, String priority, String status, OffsetDateTime dueAt, OffsetDateTime updatedAt, String source) {
    }

    public record CreateTaskRequest(@NotBlank String title, String description, String assigneeUserId, String priority, OffsetDateTime dueAt, String source) {
    }

    public record UpdateTaskRequest(String title, String description, String assigneeUserId, String priority, String status, OffsetDateTime dueAt) {
    }

    public record BoardColumn(String status, List<TaskItem> tasks) {
    }

    public record TaskBoardResponse(List<BoardColumn> columns) {
    }
}