package com.aieap.platform.notification;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Notifications")
@RequestMapping("/notifications")
public class NotificationController {
    private final ConcurrentHashMap<String, NotificationItem> notifications = new ConcurrentHashMap<>();

    public NotificationController() {
        notifications.put("note-1", new NotificationItem("note-1", "TASK_CREATED", "New task created", "Task 'Review Q1 Report' has been assigned to you", false, OffsetDateTime.now().minusMinutes(2)));
        notifications.put("note-2", new NotificationItem("note-2", "REPORT_GENERATED", "Report generated", "Weekly productivity report is ready", true, OffsetDateTime.now().minusMinutes(20)));
        notifications.put("note-3", new NotificationItem("note-3", "DOCUMENT_UPLOADED", "Document uploaded", "Annual_Report_2024.pdf uploaded", false, OffsetDateTime.now().minusHours(1)));
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<NotificationItem>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        List<NotificationItem> items = notifications.values().stream().sorted((left, right) -> right.createdAt().compareTo(left.createdAt())).toList();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "createdAt,DESC"));
    }

    @GetMapping("/recent")
    public ApiEnvelope<List<NotificationItem>> recent(HttpServletRequest request) {
        List<NotificationItem> items = notifications.values().stream().limit(5).toList();
        return ResponseFactory.success(request, items);
    }

    @PatchMapping("/{id}/read")
    public ApiEnvelope<NotificationItem> markRead(@PathVariable String id, HttpServletRequest request) {
        NotificationItem existing = notification(id);
        NotificationItem updated = new NotificationItem(existing.id(), existing.type(), existing.title(), existing.message(), true, existing.createdAt());
        notifications.put(id, updated);
        return ResponseFactory.success(request, updated);
    }

    @PatchMapping("/read-all")
    public ApiEnvelope<Map<String, String>> markAllRead(HttpServletRequest request) {
        notifications.replaceAll((key, value) -> new NotificationItem(value.id(), value.type(), value.title(), value.message(), true, value.createdAt()));
        return ResponseFactory.success(request, Map.of("status", "all-read"));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<Map<String, String>> delete(@PathVariable String id, HttpServletRequest request) {
        notifications.remove(id);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", id));
    }

    private NotificationItem notification(String id) {
        NotificationItem notification = notifications.get(id);
        if (notification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return notification;
    }

    public record NotificationItem(String id, String type, String title, String message, boolean read, OffsetDateTime createdAt) {
    }
}