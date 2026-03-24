package com.aieap.platform.ai;

import com.aieap.platform.ai.service.PromptTemplateService;
import com.aieap.platform.common.ai.AiProviderProperties;
import com.aieap.platform.common.ai.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private static final Pattern DUE_PATTERN = Pattern.compile("\\bby\\s+([A-Za-z0-9, ]{2,30})", Pattern.CASE_INSENSITIVE);

    private static final String DEFAULT_SYSTEM_PROMPT =
        "You are an enterprise automation copilot. " +
        "Always provide concise actionable responses. " +
        "If tool context is provided, ground your answer in it and mention uncertainty when evidence is weak. " +
        "When documents are provided, cite source labels in square brackets.";

    // Maps mode names to template names in the DB
    private static final Map<String, String> MODE_TO_TEMPLATE = Map.of(
        "document", "document-analyzer",
        "task", "task-extractor",
        "report", "report-generator",
        "general", "enterprise-copilot",
        "code", "code-reviewer"
    );

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;
    private final AiProviderProperties aiProviderProperties;
    private final QdrantVectorStoreClient qdrantVectorStoreClient;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public AiChatService(
        LlmClient llmClient,
        ObjectMapper objectMapper,
        PromptTemplateService promptTemplateService,
        AiProviderProperties aiProviderProperties,
        QdrantVectorStoreClient qdrantVectorStoreClient
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
        this.aiProviderProperties = aiProviderProperties;
        this.qdrantVectorStoreClient = qdrantVectorStoreClient;
    }

    public ChatResult answer(
        String mode,
        String prompt,
        List<String> attachments,
        List<AiController.ChatMessage> messageHistory
    ) {
        List<String> guardrails = new ArrayList<>();
        guardrails.add("guardrail:no-sensitive-data-leak");

        String normalizedPrompt = normalizePrompt(prompt, guardrails);
        if (normalizedPrompt.isBlank()) {
            guardrails.add("guardrail:empty-prompt");
            return buildResult("Please provide a prompt before sending a chat request.", guardrails, List.of(), GroundingContext.empty(), true);
        }

        if (!llmClient.isConfigured()) {
            guardrails.add("provider:not-configured");
            return buildResult(
                "AI provider key is missing. Ollama is running locally at http://localhost:11434. " +
                "Make sure Ollama is started and llama3.2 model is pulled (ollama pull llama3.2).",
                guardrails,
                List.of(),
                GroundingContext.empty(),
                true
            );
        }

        List<ToolCall> plannedTools = planTools(mode, normalizedPrompt, attachments);
        GroundingContext groundingContext = buildGroundingContext(normalizedPrompt, attachments, guardrails);
        String toolContext = executeTools(plannedTools, mode, normalizedPrompt, attachments, messageHistory, groundingContext);

        if (attachments != null && !attachments.isEmpty() && !groundingContext.grounded()) {
            guardrails.add("fallback:no-grounding-context");
            return buildResult(
                "I could not find relevant grounded content in the attached documents. Ask a narrower question or make sure the attachment is linked to a processed document.",
                guardrails,
                plannedTools,
                groundingContext,
                true
            );
        }

        String systemPrompt = resolveSystemPrompt(mode);

        String userPrompt = "Mode: " + safe(mode) + "\n" +
            "Conversation history:\n" + serializeHistory(messageHistory) + "\n" +
            "Tool context:\n" + toolContext + "\n" +
            "User prompt:\n" + normalizedPrompt;

        String completion;
        try {
            completion = llmClient.completion(
                clamp(systemPrompt, Math.max(500, aiProviderProperties.getMaxPromptChars() / 3)),
                clamp(userPrompt, aiProviderProperties.getMaxPromptChars()),
                new LlmClient.CompletionOptions(aiProviderProperties.getMaxOutputTokens(), 0.2)
            );
        } catch (Exception ex) {
            guardrails.add("fallback:provider-error");
            return buildResult(buildFailureFallback(groundingContext), guardrails, plannedTools, groundingContext, true);
        }

        if (groundingContext.grounded() && !containsCitation(completion, groundingContext.citations())) {
            guardrails.add("guardrail:citations-appended");
            completion = completion.trim() + "\n\nSources: " + String.join(", ", groundingContext.citations().stream().limit(3).toList());
        }

        guardrails.add("provider:ollama-compatible");
        guardrails.add("orchestration:tool-planner");
        return buildResult(completion, guardrails, plannedTools, groundingContext, false);
    }

    private String resolveSystemPrompt(String mode) {
        if (mode == null || mode.isBlank()) {
            return resolveFromTemplate("enterprise-copilot");
        }

        String templateName = MODE_TO_TEMPLATE.getOrDefault(mode.toLowerCase(Locale.ROOT), "enterprise-copilot");
        return resolveFromTemplate(templateName);
    }

    private String resolveFromTemplate(String templateName) {
        try {
            return promptTemplateService.getSystemPrompt(templateName);
        } catch (Exception ex) {
            return DEFAULT_SYSTEM_PROMPT;
        }
    }

    private List<ToolCall> planTools(String mode, String prompt, List<String> attachments) {
        String plannerSystemPrompt = "You are a tool planner. " +
            "Return ONLY valid JSON object with shape {\"tools\":[{\"name\":\"...\",\"reason\":\"...\"}]}. " +
            "Allowed names: document_lookup, task_extraction, report_outline. " +
            "Never include text outside JSON.";

        String plannerUserPrompt = "Mode: " + safe(mode) + "\n" +
            "Prompt: " + clamp(prompt, aiProviderProperties.getMaxPromptChars() / 3) + "\n" +
            "AttachmentCount: " + (attachments == null ? 0 : attachments.size());

        try {
            String rawPlan = llmClient.completion(
                plannerSystemPrompt,
                plannerUserPrompt,
                new LlmClient.CompletionOptions(aiProviderProperties.getPlannerMaxOutputTokens(), 0.0)
            );
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
        List<AiController.ChatMessage> messageHistory,
        GroundingContext groundingContext
    ) {
        List<String> sections = new ArrayList<>();

        for (ToolCall toolCall : toolCalls) {
            switch (toolCall.name()) {
                case "document_lookup" -> {
                    if (attachments != null && !attachments.isEmpty()) {
                        sections.add("tool:document_lookup\n" + groundingContext.context());
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

    private GroundingContext buildGroundingContext(String question, List<String> attachmentRefs, List<String> guardrails) {
        if (attachmentRefs == null || attachmentRefs.isEmpty()) {
            return GroundingContext.empty();
        }

        if (jdbcTemplate == null) {
            guardrails.add("fallback:db-unavailable");
            return new GroundingContext("Database unavailable for attachment retrieval.", List.of(), "none", false);
        }

        List<String> documentIds = resolveDocumentIds(attachmentRefs);
        if (documentIds.isEmpty()) {
            return new GroundingContext("No linked documents were found for the provided attachments.", List.of(), "none", false);
        }

        List<DocumentSnippet> snippets = retrieveRelevantSnippets(question, documentIds, guardrails);
        if (snippets.isEmpty()) {
            return new GroundingContext("No relevant document snippets were found.", List.of(), "none", false);
        }

        List<String> citations = snippets.stream()
            .map(DocumentSnippet::citationLabel)
            .distinct()
            .limit(6)
            .toList();

        StringBuilder builder = new StringBuilder();
        int maxContextChars = Math.max(1000, aiProviderProperties.getMaxContextChars());
        for (DocumentSnippet snippet : snippets) {
            String line = "[" + snippet.citationLabel() + "] " + snippet.content() + "\n";
            if (builder.length() + line.length() > maxContextChars) {
                break;
            }
            builder.append(line);
        }

        String retrievalMode = snippets.stream().map(DocumentSnippet::retrievalMode).filter(Objects::nonNull).findFirst().orElse("sql");
        return new GroundingContext(builder.toString().trim(), citations, retrievalMode, true);
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

    private List<String> resolveDocumentIds(List<String> attachmentRefs) {
        List<String> documentIds = new ArrayList<>();

        for (String ref : attachmentRefs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }

            documentIds.addAll(jdbcTemplate.query(
                "SELECT id::text FROM aieap.documents WHERE id::text = ? OR file_name = ?",
                (rs, rowNum) -> rs.getString("id"),
                ref,
                ref
            ));

            documentIds.addAll(jdbcTemplate.query(
                "SELECT document_id::text AS document_id FROM aieap.chat_attachments WHERE id::text = ? AND document_id IS NOT NULL",
                (rs, rowNum) -> rs.getString("document_id"),
                ref
            ));
        }

        return documentIds.stream().distinct().toList();
    }

    private List<DocumentSnippet> retrieveRelevantSnippets(String question, List<String> documentIds, List<String> guardrails) {
        List<DocumentSnippet> snippets = new ArrayList<>();
        List<Double> embedding = List.of();

        if (qdrantVectorStoreClient.isAvailable()) {
            try {
                embedding = llmClient.embedding(question);
            } catch (Exception ex) {
                guardrails.add("fallback:vector-embedding-failed");
            }
        } else {
            guardrails.add("fallback:qdrant-unavailable");
        }

        int perDocumentLimit = Math.max(1, Math.min(3, aiProviderProperties.getMaxContextChunks()));
        for (String documentId : documentIds) {
            List<DocumentSnippet> documentSnippets = List.of();
            if (!embedding.isEmpty()) {
                documentSnippets = loadRelevantChunksByVector(documentId, embedding, perDocumentLimit);
            }
            if (documentSnippets.isEmpty()) {
                documentSnippets = loadRelevantChunksBySql(documentId, question, perDocumentLimit);
            }
            snippets.addAll(documentSnippets);
        }

        return snippets.stream()
            .distinct()
            .sorted(Comparator.comparingInt(snippet -> "vector".equals(snippet.retrievalMode()) ? 0 : 1))
            .limit(Math.max(1, aiProviderProperties.getMaxContextChunks()))
            .toList();
    }

    private List<DocumentSnippet> loadRelevantChunksByVector(String documentId, List<Double> queryEmbedding, int limit) {
        try {
            List<String> vectorIds = qdrantVectorStoreClient.searchPointIds(queryEmbedding, documentId, limit);
            if (vectorIds.isEmpty()) {
                return List.of();
            }
            return loadChunksByVectorIds(documentId, vectorIds);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<DocumentSnippet> loadChunksByVectorIds(String documentId, List<String> vectorIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(vectorIds.size(), "?"));
        StringBuilder orderBy = new StringBuilder("CASE vector_id ");
        for (int i = 0; i < vectorIds.size(); i++) {
            orderBy.append("WHEN ? THEN ").append(i).append(' ');
        }
        orderBy.append("ELSE 9999 END");

        List<Object> args = new ArrayList<>();
        args.add(documentId);
        args.addAll(vectorIds);
        args.addAll(vectorIds);

        return jdbcTemplate.query(
            "SELECT citation_label, LEFT(content, 700) AS content FROM aieap.document_chunks " +
                "WHERE document_id = ?::uuid AND vector_id IN (" + placeholders + ") ORDER BY " + orderBy,
            (rs, rowNum) -> new DocumentSnippet(
                documentId,
                rs.getString("citation_label"),
                rs.getString("content"),
                "vector"
            ),
            args.toArray()
        );
    }

    private List<DocumentSnippet> loadRelevantChunksBySql(String documentId, String question, int limit) {
        try {
            return jdbcTemplate.query(
                "SELECT citation_label, LEFT(content, 700) AS content FROM aieap.document_chunks " +
                    "WHERE document_id = ?::uuid " +
                    "ORDER BY ts_rank_cd(to_tsvector('english', content), plainto_tsquery('english', ?)) DESC, chunk_index ASC LIMIT ?",
                (rs, rowNum) -> new DocumentSnippet(
                    documentId,
                    rs.getString("citation_label"),
                    rs.getString("content"),
                    "sql"
                ),
                documentId,
                question,
                limit
            );
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private String serializeHistory(List<AiController.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "(empty)";
        }

        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, history.size() - Math.max(1, aiProviderProperties.getMaxHistoryMessages()));
        for (int i = start; i < history.size(); i++) {
            AiController.ChatMessage m = history.get(i);
            builder.append(m.role()).append(": ").append(clamp(m.content(), 600)).append("\n");
        }
        return clamp(builder.toString(), aiProviderProperties.getMaxPromptChars() / 2);
    }

    private String normalizePrompt(String prompt, List<String> guardrails) {
        if (prompt == null) {
            return "";
        }
        String normalized = prompt.replaceAll("\\s+", " ").trim();
        if (normalized.length() > aiProviderProperties.getMaxPromptChars()) {
            guardrails.add("guardrail:prompt-truncated");
            return normalized.substring(0, aiProviderProperties.getMaxPromptChars());
        }
        return normalized;
    }

    private String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private boolean containsCitation(String completion, List<String> citations) {
        if (completion == null || completion.isBlank()) {
            return false;
        }
        return citations.stream().anyMatch(completion::contains);
    }

    private String buildFailureFallback(GroundingContext groundingContext) {
        if (groundingContext.grounded()) {
            return "I found relevant document evidence, but the model request failed before I could generate a full answer. Sources: " +
                String.join(", ", groundingContext.citations().stream().limit(3).toList());
        }
        return "The AI request failed. Please retry in a moment.";
    }

    private ChatResult buildResult(
        String content,
        List<String> guardrails,
        List<ToolCall> toolCalls,
        GroundingContext groundingContext,
        boolean fallbackUsed
    ) {
        return new ChatResult(
            content,
            List.copyOf(guardrails),
            toJson(toolCalls.stream().map(toolCall -> Map.of("name", toolCall.name(), "reason", toolCall.reason())).toList(), "[]"),
            toJson(Map.of(
                "grounded", groundingContext.grounded(),
                "retrievalMode", groundingContext.retrievalMode(),
                "citations", groundingContext.citations(),
                "fallbackUsed", fallbackUsed
            ),
            "{}")
        );
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "general" : value;
    }

    public record ToolCall(String name, String reason) {
    }

    private record DocumentSnippet(String documentId, String citationLabel, String content, String retrievalMode) {
    }

    private record GroundingContext(String context, List<String> citations, String retrievalMode, boolean grounded) {
        private static GroundingContext empty() {
            return new GroundingContext("No tool output.", List.of(), "none", false);
        }
    }

    public record ChatResult(String content, List<String> guardrails, String toolCallsJson, String metadataJson) {
        public ChatResult(String content, List<String> guardrails) {
            this(content, guardrails, "[]", "{}");
        }
    }
}
