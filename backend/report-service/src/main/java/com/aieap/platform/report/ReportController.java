package com.aieap.platform.report;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Reports")
public class ReportController {
    private final ConcurrentHashMap<String, ReportItem> reports = new ConcurrentHashMap<>();

    public ReportController() {
        reports.put("report-1", new ReportItem("report-1", "WEEKLY_PRODUCTIVITY", "Weekly productivity summary", "GENERATED", OffsetDateTime.now().minusHours(2), Map.of("tasksCompleted", 206, "aiRequests", 67)));
    }

    @GetMapping("/dashboard/metrics")
    public ApiEnvelope<Map<String, Object>> metrics(HttpServletRequest request) {
        return ResponseFactory.success(request, Map.of(
            "totalTasks", 248,
            "pendingTasks", 42,
            "completedTasks", 206,
            "documentsUploaded", 128,
            "aiRequestsToday", 67
        ));
    }

    @GetMapping("/dashboard/activity")
    public ApiEnvelope<List<Map<String, String>>> activity(HttpServletRequest request) {
        return ResponseFactory.success(request, List.of(
            Map.of("type", "task.created", "description", "New task assigned: Review Q1 report", "occurredAt", OffsetDateTime.now().minusMinutes(12).toString()),
            Map.of("type", "document.uploaded", "description", "Annual_Report_2024.pdf uploaded", "occurredAt", OffsetDateTime.now().minusMinutes(30).toString()),
            Map.of("type", "report.generated", "description", "Weekly productivity report is ready", "occurredAt", OffsetDateTime.now().minusHours(1).toString())
        ));
    }

    @GetMapping("/health/services")
    public ApiEnvelope<List<Map<String, String>>> services(HttpServletRequest request) {
        return ResponseFactory.success(request, List.of(
            Map.of("service", "auth-service", "status", "UP"),
            Map.of("service", "task-service", "status", "UP"),
            Map.of("service", "email-service", "status", "UP"),
            Map.of("service", "document-service", "status", "UP")
        ));
    }

    @GetMapping("/reports")
    public ApiEnvelope<PageEnvelope<ReportItem>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        List<ReportItem> items = reports.values().stream().toList();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "requestedAt,DESC"));
    }

    @PostMapping("/reports/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<ReportItem> generate(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody GenerateReportRequest generateReportRequest,
        HttpServletRequest request
    ) {
        String id = idempotencyKey == null ? UUID.randomUUID().toString() : "report-" + idempotencyKey;
        ReportItem report = new ReportItem(id, generateReportRequest.reportType(), generateReportRequest.title(), "REQUESTED", OffsetDateTime.now(), generateReportRequest.parameters());
        reports.put(id, report);
        return ResponseFactory.success(request, report);
    }

    @GetMapping("/reports/{id}")
    public ApiEnvelope<ReportItem> get(@PathVariable String id, HttpServletRequest request) {
        ReportItem report = reports.get(id);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return ResponseFactory.success(request, report);
    }

    @GetMapping("/reports/analytics")
    public ApiEnvelope<Map<String, Object>> analytics(HttpServletRequest request) {
        return ResponseFactory.success(request, Map.of(
            "weeklyProductivity", List.of(Map.of("day", "Mon", "tasks", 12, "completed", 10), Map.of("day", "Tue", "tasks", 15, "completed", 13)),
            "taskCompletionRate", List.of(Map.of("month", "Jan", "rate", 85), Map.of("month", "Feb", "rate", 88)),
            "aiUsageAnalytics", List.of(Map.of("name", "Email Processing", "value", 35), Map.of("name", "Document Analysis", "value", 25))
        ));
    }

    public record ReportItem(String id, String reportType, String title, String status, OffsetDateTime requestedAt, Map<String, Object> payload) {
    }

    public record GenerateReportRequest(@NotBlank String reportType, @NotBlank String title, Map<String, Object> parameters) {
    }
}