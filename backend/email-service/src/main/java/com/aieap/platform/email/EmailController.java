package com.aieap.platform.email;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.PageEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Emails")
@RequestMapping("/emails")
public class EmailController {
    private final ConcurrentHashMap<String, EmailItem> emails = new ConcurrentHashMap<>();

    public EmailController() {
        register(new EmailItem("email-101", "Sarah Johnson", "sarah.j@client.com", "Q1 budget review required", "Review Q1 budget and schedule a meeting.", List.of("Review Q1 budget", "Schedule stakeholder meeting"), "PENDING", "HIGH", OffsetDateTime.now().minusHours(2)));
        register(new EmailItem("email-102", "Mike Chen", "mike.chen@company.com", "Project deadline update", "Deadline moved to next Friday. Team must be notified.", List.of("Update project timeline", "Notify delivery team"), "COMPLETED", "MEDIUM", OffsetDateTime.now().minusHours(5)));
    }

    @GetMapping
    public ApiEnvelope<PageEnvelope<EmailItem>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request
    ) {
        List<EmailItem> items = emails.values().stream().sorted((left, right) -> right.receivedAt().compareTo(left.receivedAt())).toList();
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return ResponseFactory.success(request, new PageEnvelope<>(items.subList(fromIndex, toIndex), page, size, items.size(), "receivedAt,DESC"));
    }

    @GetMapping("/{id}")
    public ApiEnvelope<EmailItem> get(@PathVariable String id, HttpServletRequest request) {
        return ResponseFactory.success(request, email(id));
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<EmailItem> ingest(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody IngestEmailRequest ingestEmailRequest,
        HttpServletRequest request
    ) {
        String id = idempotencyKey == null ? UUID.randomUUID().toString() : "email-" + idempotencyKey;
        EmailItem email = new EmailItem(id, ingestEmailRequest.senderName(), ingestEmailRequest.senderEmail(), ingestEmailRequest.subject(), ingestEmailRequest.aiSummary(), List.of(), "INGESTED", ingestEmailRequest.priority(), ingestEmailRequest.receivedAt());
        emails.put(id, email);
        return ResponseFactory.success(request, email);
    }

    @PostMapping("/{id}/extract-tasks")
    public ApiEnvelope<ExtractTaskResponse> extractTasks(@PathVariable String id, HttpServletRequest request) {
        EmailItem email = email(id);
        List<ExtractedTask> extractedTasks = List.of(
            new ExtractedTask("ext-" + id + "-1", email.detectedTasks().isEmpty() ? "Follow up on email" : email.detectedTasks().get(0), 0.94),
            new ExtractedTask("ext-" + id + "-2", "Confirm execution owner", 0.82)
        );
        return ResponseFactory.success(request, new ExtractTaskResponse(id, extractedTasks));
    }

    @GetMapping("/stats")
    public ApiEnvelope<Map<String, Object>> stats(HttpServletRequest request) {
        long pending = emails.values().stream().filter(email -> "PENDING".equals(email.status())).count();
        long extractedTasks = emails.values().stream().mapToLong(email -> email.detectedTasks().size()).sum();
        return ResponseFactory.success(request, Map.of(
            "emailsProcessed", emails.size(),
            "tasksDetected", extractedTasks,
            "pendingReview", pending
        ));
    }

    private EmailItem email(String id) {
        EmailItem email = emails.get(id);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found");
        }
        return email;
    }

    private void register(EmailItem email) {
        emails.put(email.id(), email);
    }

    public record EmailItem(String id, String senderName, String senderEmail, String subject, String aiSummary, List<String> detectedTasks, String status, String priority, OffsetDateTime receivedAt) {
    }

    public record IngestEmailRequest(String senderName, @Email @NotBlank String senderEmail, @NotBlank String subject, String aiSummary, String priority, OffsetDateTime receivedAt) {
    }

    public record ExtractedTask(String id, String title, double confidence) {
    }

    public record ExtractTaskResponse(String emailId, List<ExtractedTask> tasks) {
    }
}