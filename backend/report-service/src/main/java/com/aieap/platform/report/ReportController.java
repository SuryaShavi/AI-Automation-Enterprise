package com.aieap.platform.report;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.report.kafka.KafkaEventPublisher;
import com.aieap.platform.report.kafka.events.ReportGeneratedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    public ReportController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard/metrics")
    public ApiEnvelope<Map<String, Object>> metrics(HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        Integer totalTasks = db.queryForObject("SELECT COUNT(*) FROM aieap.tasks", Integer.class);
        Integer pendingTasks = db.queryForObject("SELECT COUNT(*) FROM aieap.tasks WHERE status = 'PENDING'", Integer.class);
        Integer completedTasks = db.queryForObject("SELECT COUNT(*) FROM aieap.tasks WHERE status = 'COMPLETED'", Integer.class);
        Integer documentsUploaded = db.queryForObject("SELECT COUNT(*) FROM aieap.documents", Integer.class);
        Integer aiRequestsToday = db.queryForObject("SELECT COUNT(*) FROM aieap.reports WHERE requested_at >= NOW()::date", Integer.class);
        return ResponseFactory.success(request, Map.of(
            "totalTasks", totalTasks == null ? 0 : totalTasks,
            "pendingTasks", pendingTasks == null ? 0 : pendingTasks,
            "completedTasks", completedTasks == null ? 0 : completedTasks,
            "documentsUploaded", documentsUploaded == null ? 0 : documentsUploaded,
            "aiRequestsToday", aiRequestsToday == null ? 0 : aiRequestsToday
        ));
    }

    @GetMapping("/dashboard/activity")
    public ApiEnvelope<List<Map<String, String>>> activity(HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        List<Map<String, String>> items = db.query(
            "SELECT type, description, occurred_at FROM (" +
                " SELECT 'task.created' AS type, CONCAT('New task assigned: ', title) AS description, created_at AS occurred_at FROM aieap.tasks" +
                " UNION ALL " +
                " SELECT 'document.uploaded' AS type, CONCAT(file_name, ' uploaded') AS description, created_at AS occurred_at FROM aieap.documents" +
                " UNION ALL " +
                " SELECT 'report.generated' AS type, CONCAT(title, ' is ready') AS description, COALESCE(generated_at, requested_at) AS occurred_at" +
                " FROM aieap.reports WHERE status = 'GENERATED' OR generated_at IS NOT NULL" +
            ") activity ORDER BY occurred_at DESC LIMIT 10",
            (rs, rowNum) -> Map.of(
                "type", rs.getString("type"),
                "description", rs.getString("description"),
                "occurredAt", rs.getObject("occurred_at", OffsetDateTime.class).toString()
            )
        );
        return ResponseFactory.success(request, items);
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
        List<ReportItem> items = loadReports();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "requestedAt,DESC"));
    }

    @PostMapping("/reports/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<ReportItem> generate(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody GenerateReportRequest generateReportRequest,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String targetUserId = resolveFirstActiveUserId();
        String id = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        String payload = toJson(generateReportRequest.parameters() == null ? Map.of() : generateReportRequest.parameters());
        db.update(
            "INSERT INTO aieap.reports (id, owner_user_id, report_type, title, status, request_payload, requested_at) VALUES (?::uuid, ?::uuid, ?, ?, 'REQUESTED', ?::jsonb, NOW()) ON CONFLICT (id) DO NOTHING",
            id,
            targetUserId,
            generateReportRequest.reportType(),
            generateReportRequest.title(),
            payload
        );
        ReportItem report = report(id);
        if (kafkaEventPublisher != null) {
            String correlationId = request.getHeader("X-Correlation-ID") != null
                ? request.getHeader("X-Correlation-ID")
                : UUID.randomUUID().toString();
            kafkaEventPublisher.publish(
                "report.generated",
                id,
                new ReportGeneratedEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    targetUserId,
                    id,
                    generateReportRequest.reportType(),
                    generateReportRequest.title(),
                    report.status(),
                    OffsetDateTime.now().toString()
                ),
                correlationId
            );
        }
        return ResponseFactory.success(request, report);
    }

    @GetMapping("/reports/{id}")
    public ApiEnvelope<ReportItem> get(@PathVariable String id, HttpServletRequest request) {
        ReportItem report = report(id);
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

    private List<ReportItem> loadReports() {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, report_type, title, status, requested_at, request_payload::text AS request_payload FROM aieap.reports ORDER BY requested_at DESC",
            (rs, rowNum) -> new ReportItem(
                rs.getString("id"),
                rs.getString("report_type"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getObject("requested_at", OffsetDateTime.class),
                parsePayload(rs.getString("request_payload"))
            )
        );
    }

    private ReportItem report(String id) {
        JdbcTemplate db = requireJdbc();
        List<ReportItem> rows = db.query(
            "SELECT id::text AS id, report_type, title, status, requested_at, request_payload::text AS request_payload FROM aieap.reports WHERE id = ?::uuid",
            (rs, rowNum) -> new ReportItem(
                rs.getString("id"),
                rs.getString("report_type"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getObject("requested_at", OffsetDateTime.class),
                parsePayload(rs.getString("request_payload"))
            ),
            id
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<String, Object> parsePayload(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode report payload", ex);
        }
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private String resolveFirstActiveUserId() {
        JdbcTemplate db = requireJdbc();
        List<String> rows = db.query(
            "SELECT id::text FROM aieap.users WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
            (rs, rowNum) -> rs.getString(1)
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public record ReportItem(String id, String reportType, String title, String status, OffsetDateTime requestedAt, Map<String, Object> payload) {
    }

    public record GenerateReportRequest(@NotBlank String reportType, @NotBlank String title, Map<String, Object> parameters) {
    }
}