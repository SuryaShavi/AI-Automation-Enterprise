package com.aieap.platform.ai;

import com.aieap.platform.common.ai.LlmClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private final LlmClient llmClient;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public AiChatService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ChatResult answer(
        String mode,
        String prompt,
        List<String> attachments,
        List<AiController.ChatMessage> messageHistory
    ) {
        List<String> guardrails = new ArrayList<>();
        guardrails.add("guardrail:no-sensitive-data-leak");

        String toolContext = buildToolContext(mode, prompt, attachments);

        if (!llmClient.isConfigured()) {
            guardrails.add("provider:not-configured");
            return new ChatResult(
                "AI provider key is missing. Configure AI_PROVIDER_API_KEY to enable real LLM responses.",
                guardrails
            );
        }

        String systemPrompt = "You are an enterprise automation copilot. " +
            "Always provide concise actionable responses. " +
            "If tool context is provided, ground your answer in it and mention uncertainty when evidence is weak.";

        String userPrompt = "Mode: " + safe(mode) + "\n" +
            "Conversation history:\n" + serializeHistory(messageHistory) + "\n" +
            "Tool context:\n" + toolContext + "\n" +
            "User prompt:\n" + prompt;

        String completion = llmClient.completion(systemPrompt, userPrompt);
        guardrails.add("provider:openai-compatible");
        return new ChatResult(completion, guardrails);
    }

    private String buildToolContext(String mode, String prompt, List<String> attachments) {
        List<String> sections = new ArrayList<>();

        if (attachments != null && !attachments.isEmpty()) {
            sections.add("tool:document_lookup\n" + loadAttachmentContext(attachments));
        }

        if (prompt != null && prompt.toLowerCase().contains("task")) {
            sections.add("tool:task_hints\n- Extract owner, due date, and acceptance criteria if present.");
        }

        if (mode != null && mode.toLowerCase().contains("report")) {
            sections.add("tool:report_drafter\n- Return output with bullets: Summary, Risks, Next Actions.");
        }

        if (sections.isEmpty()) {
            return "No tool output.";
        }

        return String.join("\n\n", sections);
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

    public record ChatResult(String content, List<String> guardrails) {
    }
}
