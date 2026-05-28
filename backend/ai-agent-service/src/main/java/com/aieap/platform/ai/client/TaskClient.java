package com.aieap.platform.ai.client;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for task-service.
 * Enables AI Agent to query and create tasks on-demand.
 */
@FeignClient(name = "task-service", path = "/tasks")
public interface TaskClient {

    @GetMapping
    ApiEnvelope<PageEnvelope<TaskItem>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String priority
    );

    @GetMapping("/board")
    ApiEnvelope<TaskBoardResponse> board();

@PostMapping
    ApiEnvelope<TaskItem> create(@RequestBody CreateTaskRequest request);

    @PatchMapping("/{id}")
    ApiEnvelope<TaskItem> update(
        @PathVariable String id,
        @RequestBody UpdateTaskRequest request
    );

    // DTOs mirroring task-service
    record TaskItem(
        String id,
        String title,
        String description,
        String assigneeUserId,
        String priority,
        String status,
        OffsetDateTime dueAt,
        OffsetDateTime updatedAt,
        String source
    ) {}

    record CreateTaskRequest(
        String title,
        String description,
        String assigneeUserId,
        String priority,
        OffsetDateTime dueAt,
        String source
    ) {}

    record UpdateTaskRequest(
        String title,
        String description,
        String assigneeUserId,
        String priority,
        String status,
        OffsetDateTime dueAt
    ) {}

    record BoardColumn(String status, List<TaskItem> tasks) {}

    record TaskBoardResponse(List<BoardColumn> columns) {}
}
