package com.aieap.platform.task;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Comparator;
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
    private final ConcurrentHashMap<String, TaskItem> tasks = new ConcurrentHashMap<>();

    public TaskController() {
        register(new TaskItem("task-1", "Review Q1 financial report", "Analyze quarterly performance and prepare summary", "10000000-0000-0000-0000-000000000001", "HIGH", "IN_PROGRESS", OffsetDateTime.now().plusDays(2), OffsetDateTime.now(), "email-101"));
        register(new TaskItem("task-2", "Update project documentation", "Add new API endpoints to technical docs", "10000000-0000-0000-0000-000000000002", "MEDIUM", "PENDING", OffsetDateTime.now().plusDays(4), OffsetDateTime.now(), "email-102"));
        register(new TaskItem("task-3", "Client meeting preparation", "Prepare slides for steering committee", "10000000-0000-0000-0000-000000000002", "URGENT", "PENDING", OffsetDateTime.now().plusDays(1), OffsetDateTime.now(), "manual"));
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
        List<TaskItem> filtered = tasks.values().stream()
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
    public ApiEnvelope<TaskItem> createTask(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CreateTaskRequest createTaskRequest,
        HttpServletRequest request
    ) {
        String id = idempotencyKey == null ? UUID.randomUUID().toString() : "idem-" + idempotencyKey;
        TaskItem task = new TaskItem(id, createTaskRequest.title(), createTaskRequest.description(), createTaskRequest.assigneeUserId(), createTaskRequest.priority(), "PENDING", createTaskRequest.dueAt(), OffsetDateTime.now(), createTaskRequest.source());
        tasks.put(id, task);
        return ResponseFactory.success(request, task);
    }

    @PatchMapping("/{id}")
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
        tasks.put(id, updated);
        return ResponseFactory.success(request, updated);
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<Map<String, String>> deleteTask(@PathVariable String id, HttpServletRequest request) {
        tasks.remove(id);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", id));
    }

    @GetMapping("/board")
    public ApiEnvelope<TaskBoardResponse> board(HttpServletRequest request) {
        List<BoardColumn> columns = List.of(
            new BoardColumn("PENDING", tasks.values().stream().filter(task -> "PENDING".equals(task.status())).toList()),
            new BoardColumn("IN_PROGRESS", tasks.values().stream().filter(task -> "IN_PROGRESS".equals(task.status())).toList()),
            new BoardColumn("COMPLETED", tasks.values().stream().filter(task -> "COMPLETED".equals(task.status())).toList())
        );
        return ResponseFactory.success(request, new TaskBoardResponse(columns));
    }

    private TaskItem task(String id) {
        TaskItem task = tasks.get(id);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private void register(TaskItem task) {
        tasks.put(task.id(), task);
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