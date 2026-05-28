package com.aieap.platform.ai.client;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for email-service.
 * Enables AI Agent to list and summarize emails on-demand.
 */
@FeignClient(name = "email-service", path = "/emails")
public interface EmailClient {

    @GetMapping
    ApiEnvelope<PageEnvelope<EmailItem>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/stats")
    ApiEnvelope<Map<String, Object>> stats();

    @GetMapping("/{id}")
    ApiEnvelope<EmailItem> get(@PathVariable String id);

    // DTOs mirroring email-service
    record EmailItem(
        String id,
        String sender,
        String senderEmail,
        List<String> recipients,
        String subject,
        String bodyText,
        String bodyHtml,
        String status,
        OffsetDateTime receivedAt,
        String metadataJson
    ) {}

    record ExtractTaskRequest(String title, String description, String assigneeUserId, String priority) {}

    record ExtractTaskResponse(String taskId, String confidence) {}
}
