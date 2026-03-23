package com.aieap.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aieap.platform.common.ai.LlmClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private static final Pattern DUE_PATTERN = Pattern.compile("\\bby\\s+([A-Za-z0-9, ]{2,30})", Pattern.CASE_INSENSITIVE);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public AiChatService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public ChatResult answer(
        String mode,
        String prompt,
        List<String> attachments,
        List<AiController.ChatMessage> messageHistory
    ) {
        List<String> guardrails = new ArrayList<>();
        guardrails.add("guardrail:no-sensitive-data-leak");

        if (!llmClient.isConfigured()) {
            guardrails.add("provider:not-configured");
            return new ChatResult(
                "AI provider key is missing. Configure AI_PROVIDER_API_KEY to enable real LLM responses.",
                guardrails
            );
        }

        List<ToolCall> plannedTools = planTools(mode, prompt, attachments);
        String toolContext = executeTools(plannedTools, mode, prompt, attachments, messageHistory);

        String systemPrompt = "You are an enterprise automation copilot. " +
            "Always provide concise actionable responses. " +
            "If tool context is provided, ground your answer in it and mention uncertainty when evidence is weak.";

        String userPrompt = "Mode: " + safe(mode) + "\n" +
            "Conversation history:\n" + serializeHistory(messageHistory) + "\n" +
            "Tool context:\n" + toolContext + "\n" +
            "User prompt:\n" + prompt;

        String completion = llmClient.completion(systemPrompt, userPrompt);
        guardrails.add("provider:openai-compatible");
        guardrails.add("orchestration:tool-planner");
        return new ChatResult(completion, guardrails);
    }

    private List<ToolCall> planTools(String mode, String prompt, List<String> attachments) {
        String plannerSystemPrompt = "You are a tool planner. " +
            "Return ONLY valid JSON object with shape {\"tools\":[{\"name\":\"...\",\"reason\":\"...\"}]}. " +
            "Allowed names: document_lookup, task_extraction, report_outline. " +
            "Never include text outside JSON.";

        String plannerUserPrompt = "Mode: " + safe(mode) + "\n" +
            "Prompt: " + prompt + "\n" +
            "AttachmentCount: " + (attachments == null ? 0 : attachments.size());

        try {
            String rawPlan = llmClient.completion(plannerSystemPrompt, plannerUserPrompt);
            return parseToolPlan(rawPlan);
        } catch (Exception ex) {
            return heuristicPlan(mode, prompt, attachments);
        }
    }

    private List<ToolCall> parseToolPlan(String rawPlan) {
        List<ToolCall> tools = new ArrayList<>();
        if (rawPlan == null || rawPlan.isBlank()) {
            return tools;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPlan);
            JsonNode toolNodes = root.path("tools");
            if (toolNodes.isArray()) {
                for (JsonNode node : toolNodes) {
                    String name = node.path("name").asText("");
                    if (isAllowedTool(name)) {
                        tools.add(new ToolCall(name.toLowerCase(Locale.ROOT), node.path("reason").asText("")));
                    }
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        return tools;
    }

    private List<ToolCall> heuristicPlan(String mode, String prompt, List<String> attachments) {
        List<ToolCall> tools = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            tools.add(new ToolCall("document_lookup", "Attachments were provided"));
        }
        if (prompt != null && prompt.toLowerCase(Locale.ROOT).contains("task")) {
            tools.add(new ToolCall("task_extraction", "Prompt mentions task"));
        }
        if (mode != null && mode.toLowerCase(Locale.ROOT).contains("report")) {
            tools.add(new ToolCall("report_outline", "Mode indicates report drafting"));
        }
        return tools;
    }

    private String executeTools(
        List<ToolCall> toolCalls,
        String mode,
        String prompt,
        List<String> attachments,
        List<AiController.ChatMessage> messageHistory
    ) {
        List<String> sections = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            switch (toolCall.name()) {
                case "document_lookup" -> {
                    if (attachments != null && !attachments.isEmpty()) {
                        sections.add("tool:document_lookup\n" + loadAttachmentContext(attachments));
                    }
                }
                case "task_extraction" -> sections.add("tool:task_extraction\n" + extractTaskSignals(prompt));
                case "report_outline" -> sections.add("tool:report_outline\n" + buildReportOutline(mode, messageHistory));
                default -> {
                }
            }
        }

        if (sections.isEmpty()) {
            return "No tool output.";
        }

        return String.join("\n\n", sections);
    }

    private boolean isAllowedTool(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.equals("document_lookup") || normalized.equals("task_extraction") || normalized.equals("report_outline");
    }

    private String extractTaskSignals(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "No task-like content found.";
        }

        String normalized = prompt.replaceAll("\\s+", " ").trim();
        Matcher matcher = DUE_PATTERN.matcher(normalized);
        String due = matcher.find() ? matcher.group(1).trim() : "unknown";

        return "- candidate_task: " + normalized + "\n" +
            "- due_hint: " + due + "\n" +
            "- follow_up: confirm owner, due date, and acceptance criteria";
    }

    private String buildReportOutline(String mode, List<AiController.ChatMessage> messageHistory) {
        int historyCount = messageHistory == null ? 0 : messageHistory.size();
        return "- mode: " + safe(mode) + "\n" +
            "- history_messages: " + historyCount + "\n" +
            "- expected_sections: Summary, Risks, Next Actions";
    }

    private String loadAttachmentContext(List<String> attachmentIds) {
        if (jdbcTemplate == null) {
            return "Database unavailable for attachment retrieval.";
        }

        StringBuilder builder = new StringBuilder();
        for (String attachmentId : attachmentIds) {
            List<String> rows = jdbcTemplate.query(
                "SELECT dc.citation_label || ': ' || LEFT(dc.content, 600) AS snippet " +
                    "FROM aieap.document_chunks dc " +
                    "JOIN aieap.documents d ON d.id = dc.document_id " +
                    "WHERE d.id::text = ? OR d.file_name = ? " +
                    "ORDER BY dc.chunk_index LIMIT 5",
                (rs, rowNum) -> rs.getString("snippet"),
                attachmentId,
                attachmentId
            );

            if (rows.isEmpty()) {
                builder.append("Attachment ").append(attachmentId).append(": no chunks found.\n");
            } else {
                builder.append("Attachment ").append(attachmentId).append(":\n");
                rows.forEach(row -> builder.append("- ").append(row).append("\n"));
            }
        }

        return builder.toString();
    }

    private String serializeHistory(List<AiController.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(empty)";
        }

        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, history.size() - 8);
        for (int i = start; i < history.size(); i++) {
            AiController.ChatMessage m = history.get(i);
            builder.append(m.role()).append(": ").append(m.content()).append("\n");
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "general" : value;
    }

    public record ToolCall(String name, String reason) {
    }

    public record ChatResult(String content, List<String> guardrails) {
    }
}
