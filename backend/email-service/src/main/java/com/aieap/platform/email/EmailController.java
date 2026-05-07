package com.aieap.platform.email;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.AllowedValues;
import com.aieap.platform.common.validation.NullOrNotBlank;
import com.aieap.platform.email.kafka.KafkaEventPublisher;
import com.aieap.platform.email.kafka.events.EmailIngestedEvent;
import com.aieap.platform.email.kafka.events.ExtractedTaskCreatedEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
@Tag(name = "Emails")
@RequestMapping("/emails")
@PreAuthorize("isAuthenticated()")
public class EmailController {
    private static final double AUTO_CREATE_TASK_CONFIDENCE_THRESHOLD = 0.5;
    private final EmailAiService emailAiService;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private KafkaEventPublisher kafkaEventPublisher;

    public EmailController(EmailAiService emailAiService) {
        this.emailAiService = emailAiService;
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<EmailItem>> list(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        List<EmailItem> items = loadEmails(currentUserId(authentication), isAdmin(authentication));
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "receivedAt,DESC"));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<EmailItem> get(@PathVariable UUID id, JwtAuthenticationToken authentication, HttpServletRequest request) {
        return ResponseFactory.success(request, email(id.toString(), currentUserId(authentication), isAdmin(authentication)));
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<EmailItem> ingest(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody IngestEmailRequest ingestEmailRequest,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        JdbcTemplate db = requireJdbc();
        String userId = currentUserId(authentication);
        String id = idempotencyKey == null
            ? UUID.randomUUID().toString()
            : UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString();
        String priority = ingestEmailRequest.priority() == null || ingestEmailRequest.priority().isBlank()
            ? "MEDIUM"
            : ingestEmailRequest.priority().toUpperCase();
        OffsetDateTime receivedAt = ingestEmailRequest.receivedAt() == null ? OffsetDateTime.now() : ingestEmailRequest.receivedAt();

        String generatedSummary = ingestEmailRequest.aiSummary() == null || ingestEmailRequest.aiSummary().isBlank()
            ? emailAiService.generateSummary(ingestEmailRequest)
            : ingestEmailRequest.aiSummary();

        db.update(
            "INSERT INTO aieap.emails (id, owner_user_id, external_email_id, sender_name, sender_email, subject, body_text, body_html, ai_summary, priority, processing_status, received_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, 'INGESTED', ?) ON CONFLICT (id) DO NOTHING",
            id,
            userId,
            id,
            InputSanitizer.nullableText(ingestEmailRequest.senderName()),
            ingestEmailRequest.senderEmail().trim().toLowerCase(),
            InputSanitizer.requiredText(ingestEmailRequest.subject()),
            InputSanitizer.nullableText(ingestEmailRequest.bodyText()),
            InputSanitizer.nullableText(ingestEmailRequest.bodyHtml()),
            InputSanitizer.nullableText(generatedSummary),
            priority,
            receivedAt
        );
        EmailItem email = email(id, userId, false);
        if (kafkaEventPublisher != null) {
            String correlationId = request.getHeader("X-Correlation-ID") != null
                ? request.getHeader("X-Correlation-ID")
                : UUID.randomUUID().toString();
            kafkaEventPublisher.publish(
                "new.email.ingested",
                email.id(),
                new EmailIngestedEvent(
                    UUID.randomUUID().toString(),
                    correlationId,
                    userId,
                    email.id(),
                    email.senderEmail(),
                    email.senderName(),
                    email.subject(),
                    email.priority(),
                    OffsetDateTime.now().toString()
                ),
                correlationId
            );
        }
        return ResponseFactory.success(request, email);
    }

    @PostMapping("/{id}/extract-tasks")
    @Transactional
    public ApiEnvelope<ExtractTaskResponse> extractTasks(@PathVariable UUID id, JwtAuthenticationToken authentication, HttpServletRequest request) {
        String emailId = id.toString();
        EmailItem email = email(emailId, currentUserId(authentication), isAdmin(authentication));
        JdbcTemplate db = requireJdbc();
        List<ExtractedTask> existing = loadExtractedTasks(emailId);
        if (!existing.isEmpty()) {
            return ResponseFactory.success(request, new ExtractTaskResponse(emailId, existing));
        }

        List<ExtractedTask> extractedTasks = emailAiService.extractTasks(email);
        String correlationId = request.getHeader("X-Correlation-ID") != null
            ? request.getHeader("X-Correlation-ID")
            : UUID.randomUUID().toString();
        for (ExtractedTask task : extractedTasks) {
            db.update(
                "INSERT INTO aieap.extracted_tasks (id, email_id, suggested_title, confidence, status) VALUES (?::uuid, ?::uuid, ?, ?, 'PENDING_REVIEW')",
                task.id(), emailId, task.title(), BigDecimal.valueOf(task.confidence())
            );
            autoCreateTaskFromExtraction(db, emailId, task, correlationId);
            if (kafkaEventPublisher != null) {
                kafkaEventPublisher.publish(
                    "extracted.task.created",
                    task.id(),
                    new ExtractedTaskCreatedEvent(
                        UUID.randomUUID().toString(),
                        correlationId,
                        emailId,
                        task.id(),
                        task.title(),
                        task.confidence(),
                        OffsetDateTime.now().toString()
                    ),
                    correlationId
                );
            }
        }
        return ResponseFactory.success(request, new ExtractTaskResponse(emailId, extractedTasks));
    }

    private void autoCreateTaskFromExtraction(JdbcTemplate db, String emailId, ExtractedTask task, String correlationId) {
        if (task.confidence() < AUTO_CREATE_TASK_CONFIDENCE_THRESHOLD) {
            return;
        }

        Integer existing = db.queryForObject(
            "SELECT COUNT(*) FROM aieap.tasks WHERE metadata_json->>'eventId' = ?",
            Integer.class,
            task.id()
        );
        if (existing != null && existing > 0) {
            return;
        }

        db.update(
            "INSERT INTO aieap.tasks (id, source_email_id, title, description, priority, status, metadata_json, created_at, updated_at) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, 'MEDIUM', 'PENDING', ?::jsonb, NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            task.id(),
            emailId,
            task.title(),
            "Auto-created from email extraction (confidence=" + task.confidence() + ")",
            "{\"source\":\"email-extraction\",\"eventId\":\"" + task.id() + "\",\"correlationId\":\"" + correlationId + "\"}"
        );
    }

    @GetMapping("/stats")
    public ApiEnvelope<Map<String, Object>> stats(JwtAuthenticationToken authentication, HttpServletRequest request) {
        JdbcTemplate db = requireJdbc();
        String userId = currentUserId(authentication);
        boolean admin = isAdmin(authentication);
        Integer emailsProcessed = admin
            ? db.queryForObject("SELECT COUNT(*) FROM aieap.emails", Integer.class)
            : db.queryForObject("SELECT COUNT(*) FROM aieap.emails WHERE owner_user_id = ?::uuid", Integer.class, userId);
        Integer pending = admin
            ? db.queryForObject("SELECT COUNT(*) FROM aieap.emails WHERE processing_status = 'PENDING'", Integer.class)
            : db.queryForObject("SELECT COUNT(*) FROM aieap.emails WHERE owner_user_id = ?::uuid AND processing_status = 'PENDING'", Integer.class, userId);
        Integer extractedTasks = admin
            ? db.queryForObject("SELECT COUNT(*) FROM aieap.extracted_tasks", Integer.class)
            : db.queryForObject(
                "SELECT COUNT(*) FROM aieap.extracted_tasks et JOIN aieap.emails e ON e.id = et.email_id WHERE e.owner_user_id = ?::uuid",
                Integer.class,
                userId);
        return ResponseFactory.success(request, Map.of(
            "emailsProcessed", emailsProcessed == null ? 0 : emailsProcessed,
            "tasksDetected", extractedTasks == null ? 0 : extractedTasks,
            "pendingReview", pending == null ? 0 : pending
        ));
    }

    private EmailItem email(String id, String userId, boolean admin) {
        JdbcTemplate db = requireJdbc();
        String sql = "SELECT id::text AS id, sender_name, sender_email, subject, body_text, ai_summary, processing_status, priority, received_at " +
            "FROM aieap.emails WHERE id = ?::uuid" +
            (admin ? "" : " AND owner_user_id = ?::uuid");
        Object[] args = admin ? new Object[] { id } : new Object[] { id, userId };
        List<EmailItem> rows = db.query(
            sql,
            (rs, rowNum) -> new EmailItem(
                rs.getString("id"),
                rs.getString("sender_name"),
                rs.getString("sender_email"),
                rs.getString("subject"),
                rs.getString("body_text"),
                rs.getString("ai_summary"),
                loadExtractedTitles(rs.getString("id")),
                rs.getString("processing_status"),
                rs.getString("priority"),
                rs.getObject("received_at", OffsetDateTime.class)
            ),
            args
        );
        EmailItem email = rows.isEmpty() ? null : rows.getFirst();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found");
        }
        return email;
    }

    private List<EmailItem> loadEmails(String userId, boolean admin) {
        JdbcTemplate db = requireJdbc();
        String sql = "SELECT id::text AS id, sender_name, sender_email, subject, body_text, ai_summary, processing_status, priority, received_at " +
            "FROM aieap.emails" +
            (admin ? "" : " WHERE owner_user_id = ?::uuid") +
            " ORDER BY received_at DESC";
        Object[] args = admin ? new Object[] { } : new Object[] { userId };
        return db.query(
            sql,
            (rs, rowNum) -> new EmailItem(
                rs.getString("id"),
                rs.getString("sender_name"),
                rs.getString("sender_email"),
                rs.getString("subject"),
                rs.getString("body_text"),
                rs.getString("ai_summary"),
                loadExtractedTitles(rs.getString("id")),
                rs.getString("processing_status"),
                rs.getString("priority"),
                rs.getObject("received_at", OffsetDateTime.class)
            ),
            args
        );
    }

    private List<String> loadExtractedTitles(String emailId) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT suggested_title FROM aieap.extracted_tasks WHERE email_id = ?::uuid ORDER BY created_at DESC",
            (rs, rowNum) -> rs.getString("suggested_title"),
            emailId
        );
    }

    private List<ExtractedTask> loadExtractedTasks(String emailId) {
        JdbcTemplate db = requireJdbc();
        return db.query(
            "SELECT id::text AS id, suggested_title, confidence FROM aieap.extracted_tasks WHERE email_id = ?::uuid ORDER BY created_at DESC",
            (rs, rowNum) -> new ExtractedTask(rs.getString("id"), rs.getString("suggested_title"), rs.getBigDecimal("confidence").doubleValue()),
            emailId
        );
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private String currentUserId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        String userId = authentication.getToken().getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        return userId;
    }

    private boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public record EmailItem(
        String id,
        String senderName,
        String senderEmail,
        String subject,
        String bodyText,
        String aiSummary,
        List<String> detectedTasks,
        String status,
        String priority,
        OffsetDateTime receivedAt
    ) {
    }

    public record IngestEmailRequest(
        @NullOrNotBlank String senderName,
        @Email @NotBlank String senderEmail,
        @NotBlank String subject,
        @NullOrNotBlank String bodyText,
        @NullOrNotBlank String bodyHtml,
        @NullOrNotBlank String aiSummary,
        @AllowedValues(values = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}) String priority,
        @PastOrPresent OffsetDateTime receivedAt
    ) {
    }

    public record ExtractedTask(String id, String title, double confidence) {
    }

    public record ExtractTaskResponse(String emailId, List<ExtractedTask> tasks) {
    }
}
