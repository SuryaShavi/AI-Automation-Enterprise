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
 * Feign client for report-service.
 * Enables AI Agent to generate and fetch reports on-demand.
 */
@FeignClient(name = "report-service", path = "/reports")
public interface ReportClient {

    @GetMapping
    ApiEnvelope<PageEnvelope<ReportItem>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String type
    );

    @PostMapping("/generate")
    ApiEnvelope<ReportItem> generate(@RequestBody GenerateReportRequest request);

    @GetMapping("/{id}")
    ApiEnvelope<ReportItem> get(@PathVariable String id);

    @GetMapping("/analytics")
    ApiEnvelope<ReportAnalytics> analytics();

    // DTOs mirroring report-service
    record ReportItem(
        String id,
        String title,
        String type,
        String content,
        String status,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
    ) {}

    record GenerateReportRequest(
        String title,
        String type,
        String format
    ) {}

    record ReportAnalytics(
        int totalReports,
        Map<String, Integer> byType,
        Map<String, Integer> byStatus
    ) {}
}
