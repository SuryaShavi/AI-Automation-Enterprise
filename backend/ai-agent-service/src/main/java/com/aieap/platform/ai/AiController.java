package com.aieap.platform.ai;

import com.aieap.platform.ai.domain.ChatSession;
import com.aieap.platform.ai.service.ChatPersistenceService;
import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.AllowedValues;
import com.aieap.platform.common.validation.NullOrValidUuid;
import com.aieap.platform.common.validation.SafeStringMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "AI Agent")
@RequestMapping("/ai")
@PreAuthorize("isAuthenticated()")
public class AiController {
    private final ChatPersistenceService chatPersistenceService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public AiController(
        ChatPersistenceService chatPersistenceService,
        AiChatService aiChatService,
        ObjectMapper objectMapper
    ) {
        this.chatPersistenceService = chatPersistenceService;
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ApiEnvelope<ChatReply> chat(
        @Valid @RequestBody ChatRequest chatRequest,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UUID userId = extractUserId(authentication);
        String prompt = InputSanitizer.requiredText(chatRequest.prompt());
        UUID chatId = parseOptionalUuid(chatRequest.chatId());
        if (chatId == null) {
            chatId = UUID.randomUUID();
        }

        // Get or create chat session
        ChatSession session = chatPersistenceService.getChatSessionOrCreate(chatId, userId, prompt);

        // Load existing history BEFORE adding the new message so it isn't duplicated in context
        List<ChatPersistenceService.ChatMessageDto> history = chatPersistenceService.getChatMessages(session.getId());
        List<AiController.ChatMessage> messageHistory = history.stream()
            .map(m -> new ChatMessage(m.id(), m.role(), m.content(), m.createdAt()))
            .toList();

        // Persist user message
        chatPersistenceService.addMessage(session.getId(), "user", prompt, "[]");

        // Get AI response
        AiChatService.ChatResult chatResult = aiChatService.answer(
            chatRequest.mode(),
            prompt,
            chatRequest.attachments(),
            messageHistory
        );

        // Save assistant message
        String guardrailsJson;
        try {
            guardrailsJson = objectMapper.writeValueAsString(chatResult.guardrails());
        } catch (Exception e) {
            guardrailsJson = "[]";
        }
        com.aieap.platform.ai.domain.ChatMessage savedMessage = chatPersistenceService.addMessage(
            session.getId(), 
            "assistant", 
            chatResult.content(), 
            guardrailsJson,
            chatResult.toolCallsJson(),
            chatResult.metadataJson()
        );

        ChatMessage reply = new ChatMessage(
            savedMessage.getId().toString(),
            savedMessage.getRole(),
            savedMessage.getContent(),
            savedMessage.getCreatedAt()
        );

        return ResponseFactory.success(request, new ChatReply(session.getId().toString(), reply, chatResult.guardrails()));
    }

    @GetMapping("/chats")
    public ApiEnvelope<List<ChatSummary>> chats(
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UUID userId = extractUserId(authentication);
        List<ChatSummary> summaries = chatPersistenceService.getUserChatSessions(userId).stream()
            .map(session -> new ChatSummary(session.id(), session.title(), session.updatedAt(), session.messageCount()))
            .toList();
        return ResponseFactory.success(request, summaries);
    }

    @PostMapping("/chats")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<ChatSummary> createChat(
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UUID userId = extractUserId(authentication);
        ChatSession session = chatPersistenceService.createChatSession(userId, "New Chat");
        return ResponseFactory.success(
            request,
            new ChatSummary(session.getId().toString(), session.getTitle(), session.getUpdatedAt(), 0)
        );
    }

    @GetMapping("/chats/{id}/messages")
    public ApiEnvelope<List<ChatMessage>> messages(
        @PathVariable UUID id,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UUID userId = extractUserId(authentication);
        chatPersistenceService.getChatSession(id, userId); // Verify access

        List<ChatMessage> messages = chatPersistenceService.getChatMessages(id).stream()
            .map(m -> new ChatMessage(m.id(), m.role(), m.content(), m.createdAt()))
            .toList();
        return ResponseFactory.success(request, messages);
    }

    @PostMapping("/chats/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<AttachmentReceipt> attach(
        @PathVariable UUID id,
        @Valid @RequestBody AttachmentRequest attachmentRequest,
        JwtAuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UUID userId = extractUserId(authentication);
        chatPersistenceService.getChatSession(id, userId); // Verify access

        UUID documentId = parseOptionalUuid(attachmentRequest.documentId());
        String status = documentId == null ? "QUEUED" : "READY";
        chatPersistenceService.addAttachment(
            id,
            InputSanitizer.fileName(attachmentRequest.fileName()),
            InputSanitizer.requiredText(attachmentRequest.contentType()),
            attachmentRequest.size(),
            documentId,
            attachmentRequest.metadata() == null ? null : InputSanitizer.nullableText(attachmentRequest.metadata().get("storagePath")),
            status
        );
        return ResponseFactory.success(request, new AttachmentReceipt(id.toString(), InputSanitizer.fileName(attachmentRequest.fileName()), InputSanitizer.requiredText(attachmentRequest.contentType()), attachmentRequest.size(), status));
    }

    // Helper methods
    private UUID extractUserId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Object userIdClaim = authentication.getToken().getClaim("userId");
        JdbcTemplate db = requireJdbc();

        if (userIdClaim != null) {
            try {
                UUID userId = UUID.fromString(userIdClaim.toString());
                Integer exists = db.queryForObject(
                    "SELECT COUNT(*) FROM aieap.users WHERE id = ?::uuid AND status = 'ACTIVE'",
                    Integer.class,
                    userId.toString()
                );
                if (exists != null && exists > 0) {
                    return userId;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to subject email resolution when claim is malformed.
            }
        }

        String subjectEmail = authentication.getToken().getSubject();
        if (subjectEmail != null && !subjectEmail.isBlank()) {
            List<String> rows = db.query(
                "SELECT id::text FROM aieap.users WHERE LOWER(email) = LOWER(?) AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1),
                subjectEmail
            );
            if (!rows.isEmpty()) {
                return UUID.fromString(rows.getFirst());
            }
        }

        Object userCodeClaim = authentication.getToken().getClaim("userCode");
        if (userCodeClaim != null) {
            String userCode = userCodeClaim.toString();
            List<String> rows = db.query(
                "SELECT id::text FROM aieap.users WHERE user_code = ?::bigint AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1),
                userCode
            );
            if (!rows.isEmpty()) {
                return UUID.fromString(rows.getFirst());
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found. Please sign in again.");
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid documentId", ex);
        }
    }

    public record ChatConversation(String id, String title, OffsetDateTime updatedAt, List<ChatMessage> messages) {
    }

    public record ChatMessage(String id, String role, String content, OffsetDateTime createdAt) {
    }

    public record ChatRequest(
        @NullOrValidUuid String chatId,
        @NotBlank @Size(max = 4000) String prompt,
        @AllowedValues(values = {"document", "task", "report", "general", "code"}) String mode,
        @Size(max = 10) List<@NotBlank String> attachments
    ) {
    }

    public record ChatReply(String chatId, ChatMessage message, List<String> guardrails) {
    }

    public record ChatSummary(String id, String title, OffsetDateTime updatedAt, int messageCount) {
    }

    public record AttachmentRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 120) String contentType,
        @Positive long size,
        @NullOrValidUuid String documentId,
        @SafeStringMap(maxEntries = 8, maxKeyLength = 64, maxValueLength = 512) Map<String, String> metadata
    ) {
    }

    public record AttachmentReceipt(String chatId, String fileName, String contentType, long size, String status) {
    }
}