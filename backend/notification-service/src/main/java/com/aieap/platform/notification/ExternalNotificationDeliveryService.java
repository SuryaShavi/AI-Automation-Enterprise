package com.aieap.platform.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ExternalNotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ExternalNotificationDeliveryService.class);

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${notifications.email.from:no-reply@aieap.local}")
    private String mailFrom;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    public ExternalNotificationDeliveryService(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public void deliver(String userId, String type, String title, String message, String correlationId) {
        if (jdbcTemplate == null || userId == null || userId.isBlank()) {
            return;
        }

        String recipientEmail = resolveUserEmail(userId);
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            sendEmail(recipientEmail, type, title, message, correlationId);
        }

        String slackWebhookUrl = resolveSlackWebhook(userId);
        if (slackWebhookUrl != null && !slackWebhookUrl.isBlank()) {
            sendSlack(slackWebhookUrl, type, title, message, correlationId);
        }
    }

    private String resolveUserEmail(String userId) {
        try {
            List<String> emails = jdbcTemplate.query(
                "SELECT email FROM aieap.users WHERE id = ?::uuid LIMIT 1",
                (rs, rowNum) -> rs.getString("email"),
                userId
            );
            return emails.isEmpty() ? null : emails.getFirst();
        } catch (Exception ex) {
            log.warn("Could not resolve user email for {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private String resolveSlackWebhook(String userId) {
        try {
            List<String> configs = jdbcTemplate.query(
                "SELECT config_json::text FROM aieap.integrations WHERE user_id = ?::uuid AND LOWER(provider) = 'slack' AND status = 'CONNECTED' ORDER BY updated_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1),
                userId
            );
            if (configs.isEmpty()) {
                return null;
            }

            Map<String, Object> config = objectMapper.readValue(configs.getFirst(), new TypeReference<Map<String, Object>>() { });
            Object webhookUrl = config.get("webhookUrl");
            if (!(webhookUrl instanceof String) || ((String) webhookUrl).isBlank()) {
                webhookUrl = config.get("webhook_url");
            }
            return webhookUrl instanceof String ? ((String) webhookUrl).trim() : null;
        } catch (Exception ex) {
            log.warn("Could not resolve Slack webhook for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private void sendEmail(String recipient, String type, String title, String message, String correlationId) {
        if (javaMailSender == null) {
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(recipient);
            mail.setSubject("[AIEAP] " + title);
            mail.setText("Type: " + type + "\n\n" + message + "\n\nCorrelation ID: " + correlationId);
            javaMailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Email notification delivery failed for {}: {}", recipient, ex.getMessage());
        }
    }

    private void sendSlack(String webhookUrl, String type, String title, String message, String correlationId) {
        try {
            RestClient restClient = restClientBuilder.baseUrl(webhookUrl).build();
            restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "text",
                    "[" + type + "] " + title + "\n" + message + "\nCorrelation ID: " + correlationId
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Slack webhook delivery failed: {}", ex.getMessage());
        }
    }
}
