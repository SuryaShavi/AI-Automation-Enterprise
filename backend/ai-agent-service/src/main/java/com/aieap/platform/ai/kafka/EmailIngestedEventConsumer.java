package com.aieap.platform.ai.kafka;

import com.aieap.platform.ai.service.AiEmailAgentService;
import com.aieap.platform.ai.service.AiEmailAgentService.EmailProcessingResult;
import com.aieap.platform.ai.service.AiEmailAgentService.StructuredTask;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Email full pipeline: email-service -> Kafka -> ai-agent-service EmailAgent -> publish extracted.task.created.
 */
@Component
public class EmailIngestedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestedEventConsumer.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private final AiEmailAgentService aiEmailAgentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public EmailIngestedEventConsumer(AiEmailAgentService aiEmailAgentService,
        KafkaTemplate<String, Object> kafkaTemplate) {
        this.aiEmailAgentService = aiEmailAgentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "new.email.ingested", groupId = "ai-agent-service-email")
    public void onEmailIngested(
        @Payload Map<String, Object> event,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "X-Correlation-ID", required = false) byte[] correlationIdBytes
    ) {
        String correlationId = correlationIdBytes != null
            ? new String(correlationIdBytes, StandardCharsets.UTF_8)
            : "unknown";

        String emailId = (String) event.get("emailId");
        String senderName = (String) event.get("senderName");
        String subject = (String) event.getOrDefault("subject", "");
        String priority = (String) event.getOrDefault("priority", "MEDIUM");

        String bodyText = (String) event.getOrDefault("bodyText", "");
        if (bodyText == null || bodyText.isBlank()) {
            // email-service currently publishes without bodyText; fetch it from Postgres (if available).
            bodyText = fetchEmailBodyFromDb(emailId);
        }

        String emailContent = "Sender: " + (senderName == null ? "" : senderName)
            + "\nSubject: " + (subject == null ? "" : subject)
            + "\nPriority: " + (priority == null ? "MEDIUM" : priority)
            + "\nBody: " + (bodyText == null ? "" : bodyText);

        log.info("[KAFKA] new.email.ingested correlationId={} emailId={} subject='{}' bodyLen={}",
            correlationId,
            emailId,
            subject,
            bodyText == null ? 0 : bodyText.length()
        );

EmailProcessingResult result = aiEmailAgentService.processEmail(emailContent);

        List<StructuredTask> tasks = result.tasks();

        if (tasks == null || tasks.isEmpty()) {
            log.info("No tasks extracted for emailId={} correlationId={}", emailId, correlationId);
            return;
        }

        for (StructuredTask task : tasks) {
            Map<String, Object> extractedEvent = new HashMap<>();
            extractedEvent.put("eventId", emailId + ":" + task.description());
            extractedEvent.put("correlationId", correlationId);
            extractedEvent.put("emailId", emailId);
            extractedEvent.put("taskId", emailId + ":task:" + task.description());
            extractedEvent.put("suggestedTitle", task.description());
            extractedEvent.put("confidence", 1.0);
            extractedEvent.put("occurredAt", OffsetDateTime.now().toString());

            org.apache.kafka.clients.producer.ProducerRecord<String, Object> record =
                new org.apache.kafka.clients.producer.ProducerRecord<>(
                    "extracted.task.created",
                    (String) extractedEvent.get("taskId"),
                    extractedEvent
                );
            record.headers().add("X-Correlation-ID", correlationId.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record);

            log.info("Published extracted.task.created emailId={} task='{}'", emailId, task.description());
        }
    }

    private String fetchEmailBodyFromDb(String emailId) {
        if (jdbcTemplate == null || emailId == null || emailId.isBlank()) {
            return "";
        }
        try {
            // Use the email-service schema. This service shares the same DB in docker-compose.
            return jdbcTemplate.queryForObject(
                "SELECT body_text FROM aieap.emails WHERE id = ?::uuid",
                String.class,
                emailId
            );
        } catch (Exception e) {
            return "";
        }
    }
}

