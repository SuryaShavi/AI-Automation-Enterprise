package com.aieap.platform.ai.service;

import com.aieap.platform.common.ai.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiEmailAgentService {
    private static final Logger log = LoggerFactory.getLogger(AiEmailAgentService.class);
    private final LlmClient llmClient;

    @Autowired
    public AiEmailAgentService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public EmailProcessingResult processEmail(String emailContent) {
        // Summary
        String summaryPrompt = "You are an executive assistant. Summarize this email (2-3 sentences), highlight action items: " + emailContent;
        String summary = llmClient.completion("Email Assistant", summaryPrompt, null);

        // Task extraction as JSON
        String taskPrompt = "Extract main task as JSON ONLY: {\"description\": \"...\", \"deadline\": \"...\", \"assignee\": \"...\", \"priority\": \"low|medium|high\"}. No task = {}. Email: " + emailContent;
        String taskJson = llmClient.completion("Task Extractor", taskPrompt, null);
        StructuredTask task = parseTaskJson(taskJson);

        log.info("Email processed: tasks found = {}", task != null);
        return new EmailProcessingResult(summary, task != null ? List.of(task) : List.of());
    }

    private StructuredTask parseTaskJson(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) return null;
        try {
            return new StructuredTask(
                extractField(json, "description"),
                extractField(json, "deadline"),
                extractField(json, "assignee"),
                extractField(json, "priority")
            );
        } catch (Exception e) {
            log.warn("JSON parse failed: {}", json);
            return null;
        }
    }

    private String extractField(String json, String field) {
        String patternStr = "\"" + field + "\":\\s*\"([^\\\"]*)\"";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    public record EmailProcessingResult(String summary, List<StructuredTask> tasks) {}
    public record StructuredTask(String description, String deadline, String assignee, String priority) {}
}

