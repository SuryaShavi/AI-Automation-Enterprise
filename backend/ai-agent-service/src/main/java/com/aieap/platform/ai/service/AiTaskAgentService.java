package com.aieap.platform.ai.service;

import com.aieap.platform.common.ai.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiTaskAgentService {
    private static final Logger log = LoggerFactory.getLogger(AiTaskAgentService.class);
    private final LlmClient llmClient;

    @Autowired
    public AiTaskAgentService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public StructuredTask extractFromText(String text) {
        String prompt = "Extract task as JSON ONLY: {\"description\": \"...\", \"deadline\": \"...\", \"assignee\": \"...\", \"priority\": \"low|medium|high\"}. No task = {}. Text: " + text;
        String json = llmClient.completion("Task Extractor", prompt, null);
        log.info("Task extraction JSON: {}", json);
        return parseTaskJson(json);
    }

    private StructuredTask parseTaskJson(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) return null;
        return new StructuredTask(
            extractField(json, "description"),
            extractField(json, "deadline"),
            extractField(json, "assignee"),
            extractField(json, "priority")
        );
    }

    private String extractField(String json, String field) {
        String patternStr = "\"" + field + "\":\\s*\"([^\\\"]*)\"";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    public record StructuredTask(String description, String deadline, String assignee, String priority) {}
}

