package com.aieap.platform.email;

import com.aieap.platform.common.ai.AiProviderProperties;
import com.aieap.platform.common.ai.LlmClient;
import com.aieap.platform.common.ai.TokenEstimator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailAiService {
    private static final Pattern INJECTION_PATTERN = Pattern.compile("(?i)(ignore\\s+previous|reveal\\s+system\\s+prompt|developer\\s+message|jailbreak)");

    private final LlmClient llmClient;
    private final AiProviderProperties aiProviderProperties;
    private final TokenEstimator tokenEstimator;
    private final ObjectMapper objectMapper;

    public EmailAiService(
        LlmClient llmClient,
        AiProviderProperties aiProviderProperties,
        TokenEstimator tokenEstimator,
        ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.aiProviderProperties = aiProviderProperties;
        this.tokenEstimator = tokenEstimator;
        this.objectMapper = objectMapper;
    }

    public String generateSummary(EmailController.IngestEmailRequest request) {
        String subject = normalize(request.subject());
        String body = normalize(request.bodyText());
        String sender = normalize(request.senderName()) + " <" + normalize(request.senderEmail()) + ">";

        if (!llmClient.isConfigured()) {
            return fallbackSummary(subject, body);
        }

        String safeBody = sanitizeBody(body);
        String systemPrompt = "You summarize enterprise emails. Return a concise summary in 2 sentences max and keep actionability.";
        String userPrompt = "Sender: " + sender + "\nSubject: " + subject + "\nBody: " + safeBody;

        userPrompt = clampPrompt(userPrompt);

        try {
            String completion = llmClient.completion(
                systemPrompt,
                userPrompt,
                new LlmClient.CompletionOptions(Math.min(180, aiProviderProperties.getMaxOutputTokens()), 0.1)
            );
            return normalize(completion).isBlank() ? fallbackSummary(subject, body) : normalize(completion);
        } catch (Exception ex) {
            return fallbackSummary(subject, body);
        }
    }

    public List<EmailController.ExtractedTask> extractTasks(EmailController.EmailItem email) {
        String subject = normalize(email.subject());
        String body = normalize(email.bodyText());
        String summary = normalize(email.aiSummary());

        if (!llmClient.isConfigured()) {
            return fallbackTasks(subject, summary);
        }

        String systemPrompt = "Extract actionable tasks from enterprise emails. " +
            "Return ONLY strict JSON: {\"tasks\":[{\"title\":\"...\",\"confidence\":0.0,\"description\":\"...\"}]}. " +
            "confidence must be between 0 and 1.";
        String userPrompt = "Subject: " + subject + "\nSummary: " + summary + "\nBody: " + sanitizeBody(body);
        userPrompt = clampPrompt(userPrompt);

        try {
            String completion = llmClient.completion(
                systemPrompt,
                userPrompt,
                new LlmClient.CompletionOptions(Math.min(260, aiProviderProperties.getMaxOutputTokens()), 0.0)
            );
            List<EmailController.ExtractedTask> parsed = parseTasks(completion);
            return parsed.isEmpty() ? fallbackTasks(subject, summary) : parsed;
        } catch (Exception ex) {
            return fallbackTasks(subject, summary);
        }
    }

    private List<EmailController.ExtractedTask> parseTasks(String raw) {
        List<EmailController.ExtractedTask> tasks = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return tasks;
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode taskNodes = root.path("tasks");
            if (!taskNodes.isArray()) {
                return List.of();
            }

            for (JsonNode taskNode : taskNodes) {
                String title = normalize(taskNode.path("title").asText(""));
                if (title.isBlank()) {
                    continue;
                }

                double confidence = taskNode.path("confidence").asDouble(0.75);
                confidence = Math.max(0.0, Math.min(1.0, confidence));
                tasks.add(new EmailController.ExtractedTask(UUID.randomUUID().toString(), title, confidence));
                if (tasks.size() >= 5) {
                    break;
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }

        return tasks;
    }

    private List<EmailController.ExtractedTask> fallbackTasks(String subject, String summary) {
        String primary = subject.isBlank() ? "Follow up on email" : "Action required: " + truncate(subject, 60);
        String secondary = summary.isBlank() ? "Confirm owner and due date" : "Review summary and assign owner";
        return List.of(
            new EmailController.ExtractedTask(UUID.randomUUID().toString(), primary, 0.78),
            new EmailController.ExtractedTask(UUID.randomUUID().toString(), secondary, 0.66)
        );
    }

    private String fallbackSummary(String subject, String body) {
        String base = subject.isBlank() ? body : subject + ". " + truncate(body, 180);
        return truncate(base.replaceAll("\\s+", " ").trim(), 220);
    }

    private String clampPrompt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String byChars = value.length() > aiProviderProperties.getMaxPromptChars()
            ? value.substring(0, aiProviderProperties.getMaxPromptChars())
            : value;

        if (tokenEstimator.estimate(byChars) > aiProviderProperties.getMaxPromptTokens()) {
            return tokenEstimator.truncateToTokens(byChars, aiProviderProperties.getMaxPromptTokens());
        }

        return byChars;
    }

    private String sanitizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = normalize(body);
        if (INJECTION_PATTERN.matcher(normalized).find()) {
            return "[filtered potentially unsafe instructions from email body]";
        }
        return truncate(normalized, 4000);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
