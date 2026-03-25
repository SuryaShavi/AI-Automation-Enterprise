package com.aieap.platform.notification.kafka;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes new.email.ingested, report.generated, and workflow.state_changed events
 * and writes IN_APP notifications into aieap.notifications.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // new.email.ingested
    // -------------------------------------------------------------------------
    @KafkaListener(topics = "new.email.ingested", groupId = "notification-service")
    public void onEmailIngested(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = extractCorrelationId(correlationIdBytes);
        String targetUserId = (String) event.get("targetUserId");
        String senderName = (String) event.getOrDefault("senderName", "Unknown");
        String subject = (String) event.getOrDefault("subject", "(no subject)");
        String priority = (String) event.getOrDefault("priority", "MEDIUM");

        log.info("[KAFKA] Received new.email.ingested correlationId={} sender='{}' subject='{}'",
            correlationId, senderName, subject);

        createNotification(
            "EMAIL_RECEIVED",
            "New email from " + senderName,
            "Subject: " + subject + " [Priority: " + priority + "]",
            targetUserId,
            correlationId
        );
    }

    // -------------------------------------------------------------------------
    // report.generated
    // -------------------------------------------------------------------------
    @KafkaListener(topics = "report.generated", groupId = "notification-service")
    public void onReportGenerated(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = extractCorrelationId(correlationIdBytes);
        String targetUserId = (String) event.get("targetUserId");
        String title = (String) event.getOrDefault("title", "Report");
        String reportType = (String) event.getOrDefault("reportType", "");

        log.info("[KAFKA] Received report.generated correlationId={} title='{}' type='{}'",
            correlationId, title, reportType);

        createNotification(
            "REPORT_READY",
            "Report ready: " + title,
            "Your " + reportType + " report has been generated and is ready to view.",
            targetUserId,
            correlationId
        );
    }

    // -------------------------------------------------------------------------
    // workflow.state_changed
    // -------------------------------------------------------------------------
    @KafkaListener(topics = "workflow.state_changed", groupId = "notification-service")
    public void onWorkflowStateChanged(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = extractCorrelationId(correlationIdBytes);
        String targetUserId = (String) event.get("targetUserId");
        String workflowName = (String) event.getOrDefault("workflowName", "Workflow");
        String previousStatus = (String) event.getOrDefault("previousStatus", "");
        String newStatus = (String) event.getOrDefault("newStatus", "");

        log.info("[KAFKA] Received workflow.state_changed correlationId={} workflow='{}' {}->{}",
            correlationId, workflowName, previousStatus, newStatus);

        createNotification(
            "WORKFLOW_STATE_CHANGED",
            "Workflow updated: " + workflowName,
            "Status changed from " + previousStatus + " to " + newStatus + ".",
            targetUserId,
            correlationId
        );
    }

    // -------------------------------------------------------------------------
    // Shared helper: persist notification for first active user
    // -------------------------------------------------------------------------
    private void createNotification(String type, String title, String message, String targetUserId, String correlationId) {
        if (jdbcTemplate == null) {
            log.warn("[KAFKA] JdbcTemplate unavailable — skipping notification creation correlationId={}", correlationId);
            return;
        }

        // Prefer event target user; fall back to first active user when absent.
        String userId = (targetUserId != null && !targetUserId.isBlank()) ? targetUserId : resolveUserId();
        if (userId == null) {
            log.warn("[KAFKA] No active user found — skipping notification '{}' correlationId={}", title, correlationId);
            return;
        }

        jdbcTemplate.update(
            "INSERT INTO aieap.notifications " +
            "(id, user_id, channel, notification_type, title, message, status, metadata_json, created_at) " +
            "VALUES (?::uuid, ?::uuid, 'IN_APP', ?, ?, ?, 'UNREAD', ?::jsonb, NOW())",
            UUID.randomUUID().toString(),
            userId,
            type,
            title,
            message,
            "{\"correlationId\":\"" + correlationId + "\"}"
        );

        log.info("[KAFKA] Notification created type={} correlationId={}", type, correlationId);
    }

    private String resolveUserId() {
        List<String> rows = jdbcTemplate.query(
            "SELECT id::text FROM aieap.users WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
            (rs, rowNum) -> rs.getString(1)
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String extractCorrelationId(byte[] bytes) {
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "unknown";
    }
}
