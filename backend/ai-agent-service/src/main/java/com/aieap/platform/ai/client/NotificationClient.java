package com.aieap.platform.ai.client;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for notification-service.
 * Enables AI Agent to fetch user notifications on-demand.
 */
@FeignClient(name = "notification-service", path = "/notifications")
public interface NotificationClient {

    @GetMapping
    ApiEnvelope<PageEnvelope<NotificationItem>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/recent")
    ApiEnvelope<List<NotificationItem>> recent(@RequestParam(defaultValue = "10") int limit);

    @PatchMapping("/{id}/read")
    ApiEnvelope<NotificationItem> markRead(@PathVariable String id);

    // DTOs mirroring notification-service
    record NotificationItem(
        String id,
        String userId,
        String channel,
        String notificationType,
        String title,
        String message,
        String status,
        OffsetDateTime createdAt,
        String metadataJson
    ) {}
}
