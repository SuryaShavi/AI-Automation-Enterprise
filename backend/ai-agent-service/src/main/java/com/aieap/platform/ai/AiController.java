package com.aieap.platform.ai;

import com.aieap.platform.ai.domain.ChatSession;
import com.aieap.platform.ai.service.ChatPersistenceService;
import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
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
public class AiController {
    private final ChatPersistenceService chatPersistenceService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

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
        UUID chatId = chatRequest.chatId() == null || chatRequest.chatId().isBlank() 
            ? UUID.randomUUID() 
            : UUID.fromString(chatRequest.chatId());

        // Get or create chat session
        ChatSession session = chatPersistenceService.getChatSessionOrCreate(chatId, userId, chatRequest.prompt());

        // Load existing history BEFORE adding the new message so it isn't duplicated in context
        List<ChatPersistenceService.ChatMessageDto> history = chatPersistenceService.getChatMessages(session.getId());
        List<AiController.ChatMessage> messageHistory = history.stream()
            .map(m -> new ChatMessage(m.id(), m.role(), m.content(), m.createdAt()))
            .collect(Collectors.toList());

        // Persist user message
        chatPersistenceService.addMessage(session.getId(), "user", chatRequest.prompt(), "[]");

        // Get AI response
        AiChatService.ChatResult chatResult = aiChatService.answer(
            chatRequest.mode(),
            chatRequest.prompt(),
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
            .collect(Collectors.toList());
        return ResponseFactory.success(request, summaries);
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
            .collect(Collectors.toList());
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
            attachmentRequest.fileName(),
            attachmentRequest.contentType(),
            attachmentRequest.size(),
            documentId,
            attachmentRequest.metadata() == null ? null : attachmentRequest.metadata().get("storagePath"),
            status
        );
        return ResponseFactory.success(request, new AttachmentReceipt(id.toString(), attachmentRequest.fileName(), attachmentRequest.contentType(), attachmentRequest.size(), status));
    }

    // Helper methods
    private UUID extractUserId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Object userIdClaim = authentication.getToken().getClaim("userId");
        if (userIdClaim == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found in token");
        }
        try {
            return UUID.fromString(userIdClaim.toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user ID in token", ex);
        }
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

    public record ChatRequest(String chatId, @NotBlank String prompt, String mode, List<String> attachments) {
    }

    public record ChatReply(String chatId, ChatMessage message, List<String> guardrails) {
    }

    public record ChatSummary(String id, String title, OffsetDateTime updatedAt, int messageCount) {
    }

    public record AttachmentRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        long size,
        String documentId,
        Map<String, String> metadata
    ) {
    }

    public record AttachmentReceipt(String chatId, String fileName, String contentType, long size, String status) {
    }
}