package com.aieap.platform.notification;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Notifications")
@RequestMapping("/notifications")
public class NotificationController {
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public ApiEnvelope<PageEnvelope<NotificationItem>> list(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        List<NotificationItem> items = loadNotifications();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "createdAt,DESC"));
    }

    @GetMapping("/recent")
    public ApiEnvelope<List<NotificationItem>> recent(HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        List<NotificationItem> items = db.query(
            "SELECT id::text AS id, notification_type, title, message, read_at, created_at FROM aieap.notifications ORDER BY created_at DESC LIMIT 5",
            (rs, rowNum) -> new NotificationItem(
                rs.getString("id"),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getObject("read_at", OffsetDateTime.class) != null,
                rs.getObject("created_at", OffsetDateTime.class)
            )
        );
        return ResponseFactory.success(request, items);
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ApiEnvelope<NotificationItem> markRead(@PathVariable UUID id, HttpServletRequest request) {
        String notificationId = id.toString();
        JdbcTemplate db = requireJdbc();
        db.update("UPDATE aieap.notifications SET read_at = NOW(), status = 'READ' WHERE id = ?::uuid", notificationId);
        return ResponseFactory.success(request, notification(notificationId));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ApiEnvelope<Map<String, String>> markAllRead(HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        db.update("UPDATE aieap.notifications SET read_at = NOW(), status = 'READ' WHERE read_at IS NULL");
        return ResponseFactory.success(request, Map.of("status", "all-read"));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiEnvelope<Map<String, String>> delete(@PathVariable UUID id, HttpServletRequest request) {
        String notificationId = id.toString();
        JdbcTemplate db = requireJdbc();
        db.update("DELETE FROM aieap.notifications WHERE id = ?::uuid", notificationId);
        return ResponseFactory.success(request, Map.of("status", "deleted", "id", notificationId));
    }

    private NotificationItem notification(String id) {
        JdbcTemplate db = requireJdbc();
        List<NotificationItem> rows = db.query(
            "SELECT id::text AS id, notification_type, title, message, read_at, created_at FROM aieap.notifications WHERE id = ?::uuid",
            (rs, rowNum) -> new NotificationItem(
                rs.getString("id"),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getObject("read_at", OffsetDateTime.class) != null,
                rs.getObject("created_at", OffsetDateTime.class)
            ),
            id
        );
        NotificationItem notification = rows.isEmpty() ? null : rows.getFirst();
        if (notification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return notification;
    }

    private List<NotificationItem> loadNotifications() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, notification_type, title, message, read_at, created_at FROM aieap.notifications ORDER BY created_at DESC",
            (rs, rowNum) -> new NotificationItem(
                rs.getString("id"),
                rs.getString("notification_type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getObject("read_at", OffsetDateTime.class) != null,
                rs.getObject("created_at", OffsetDateTime.class)
            )
        );
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    public record NotificationItem(String id, String type, String title, String message, boolean read, OffsetDateTime createdAt) {
    }
}