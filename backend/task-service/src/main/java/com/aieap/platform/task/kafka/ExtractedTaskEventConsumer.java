package com.aieap.platform.task.kafka;

import java.nio.charset.StandardCharsets;
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
 * Consumes extracted.task.created events from email-service and auto-creates tasks
 * in aieap.tasks when confidence exceeds the threshold.
 */
@Component
public class ExtractedTaskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExtractedTaskEventConsumer.class);
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @KafkaListener(topics = "extracted.task.created", groupId = "task-service")
    public void onExtractedTask(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = correlationIdBytes != null
            ? new String(correlationIdBytes, StandardCharsets.UTF_8)
            : "unknown";

        String emailId = (String) event.get("emailId");
        String eventId = (String) event.get("eventId");
        String suggestedTitle = (String) event.get("suggestedTitle");
        Object rawConfidence = event.get("confidence");
        double confidence = rawConfidence instanceof Number ? ((Number) rawConfidence).doubleValue() : 0.0;

        log.info("[KAFKA] Received extracted.task.created correlationId={} emailId={} title='{}' confidence={}",
            correlationId, emailId, suggestedTitle, confidence);

        if (jdbcTemplate == null) {
            log.warn("[KAFKA] JdbcTemplate unavailable — skipping task creation for correlationId={}", correlationId);
            return;
        }

        if (confidence < CONFIDENCE_THRESHOLD) {
            log.info("[KAFKA] Confidence {} below threshold {} — skipping auto-creation correlationId={}",
                confidence, CONFIDENCE_THRESHOLD, correlationId);
            return;
        }

        if (eventId == null || eventId.isBlank()) {
            eventId = "missing-event-id";
        }

        Integer alreadyProcessed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM aieap.tasks WHERE metadata_json->>'eventId' = ?",
            Integer.class,
            eventId
        );
        if (alreadyProcessed != null && alreadyProcessed > 0) {
            log.info("[KAFKA] Duplicate extracted.task.created ignored eventId={} correlationId={}", eventId, correlationId);
            return;
        }

        String taskId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO aieap.tasks " +
            "(id, source_email_id, title, description, priority, status, metadata_json, created_at, updated_at) " +
            "VALUES (?::uuid, ?::uuid, ?, ?, 'MEDIUM', 'PENDING', ?::jsonb, NOW(), NOW()) " +
            "ON CONFLICT DO NOTHING",
            taskId,
            parseUuidOrNull(emailId),
            suggestedTitle,
            "Auto-created from email extraction (confidence=" + confidence + ")",
            "{\"source\":\"email-extraction\",\"eventId\":\"" + eventId + "\",\"correlationId\":\"" + correlationId + "\"}"
        );

        log.info("[KAFKA] Task auto-created id={} from emailId={} correlationId={}", taskId, emailId, correlationId);
    }

    private String parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
