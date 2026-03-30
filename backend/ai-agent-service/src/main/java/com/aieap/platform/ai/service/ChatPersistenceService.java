package com.aieap.platform.ai.service;

import com.aieap.platform.ai.domain.ChatSession;
import com.aieap.platform.ai.domain.ChatMessage;
import com.aieap.platform.ai.domain.ChatAttachment;
import com.aieap.platform.ai.repository.ChatSessionRepository;
import com.aieap.platform.ai.repository.ChatMessageRepository;
import com.aieap.platform.ai.repository.ChatAttachmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatPersistenceService {
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;

    public ChatPersistenceService(
        ChatSessionRepository chatSessionRepository,
        ChatMessageRepository chatMessageRepository,
        ChatAttachmentRepository chatAttachmentRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatAttachmentRepository = chatAttachmentRepository;
    }

    // Chat Session Operations
    public ChatSession createChatSession(UUID userId, String title) {
        ChatSession session = new ChatSession(userId, title);
        return chatSessionRepository.save(session);
    }

    public ChatSession getChatSession(UUID sessionId, UUID userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    public ChatSession getChatSessionOrCreate(UUID sessionId, UUID userId, String firstMessage) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseGet(() -> {
                String title = firstMessage.substring(0, Math.min(firstMessage.length(), 50));
                ChatSession session = new ChatSession(userId, title);
                return chatSessionRepository.save(session);
            });
    }

    public List<ChatSessionDto> getUserChatSessions(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(this::toChatSessionDto)
            .collect(Collectors.toList());
    }

    public void updateChatSession(ChatSession session) {
        session.setUpdatedAt(OffsetDateTime.now());
        chatSessionRepository.save(session);
    }

    public void deleteChatSession(UUID sessionId, UUID userId) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        chatSessionRepository.delete(session);
    }

    // Chat Message Operations
    public ChatMessage addMessage(UUID sessionId, String role, String content, String guardrails) {
        return addMessage(sessionId, role, content, guardrails, "[]", "{}");
    }

    public ChatMessage addMessage(
        UUID sessionId,
        String role,
        String content,
        String guardrails,
        String toolCalls,
        String metadataJson
    ) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));

        ChatMessage message = new ChatMessage(session, role, content, guardrails);
        message.setToolCalls(toolCalls == null || toolCalls.isBlank() ? "[]" : toolCalls);
        message.setMetadataJson(metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson);
        ChatMessage saved = chatMessageRepository.save(message);

        // Touch the session updated_at
        session.setUpdatedAt(OffsetDateTime.now());
        chatSessionRepository.save(session);

        return saved;
    }

    public List<ChatMessageDto> getChatMessages(UUID sessionId) {
        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId).stream()
            .map(this::toChatMessageDto)
            .collect(Collectors.toList());
    }

    // Chat Attachment Operations
    public ChatAttachment addAttachment(UUID sessionId, String fileName, String contentType, Long fileSize) {
        return addAttachment(sessionId, fileName, contentType, fileSize, null, null, null);
    }

    public ChatAttachment addAttachment(
        UUID sessionId,
        String fileName,
        String contentType,
        Long fileSize,
        UUID documentId,
        String storagePath,
        String processingStatus
    ) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));

        String resolvedStatus = (processingStatus == null || processingStatus.isBlank())
            ? (documentId == null ? "QUEUED" : "READY")
            : processingStatus;

        ChatAttachment attachment = new ChatAttachment(session, fileName, contentType, fileSize);
        attachment.setDocumentId(documentId);
        attachment.setStoragePath(storagePath);
        attachment.setProcessingStatus(resolvedStatus);

        return chatAttachmentRepository.save(attachment);
    }

    public List<ChatAttachmentDto> getAttachments(UUID sessionId) {
        return chatAttachmentRepository.findByChatSessionIdOrderByUploadedAtDesc(sessionId).stream()
            .map(this::toChatAttachmentDto)
            .collect(Collectors.toList());
    }

    // DTO Records
    public record ChatSessionDto(
        String id,
        String title,
        String summary,
        OffsetDateTime updatedAt,
        int messageCount
    ) {}

    public record ChatMessageDto(
        String id,
        String role,
        String content,
        String toolCalls,
        String guardrails,
        String metadataJson,
        OffsetDateTime createdAt
    ) {}

    public record ChatAttachmentDto(
        String id,
        String fileName,
        String contentType,
        long fileSize,
        String documentId,
        String processingStatus,
        OffsetDateTime uploadedAt
    ) {}

    // Private mapping methods
    private ChatSessionDto toChatSessionDto(ChatSession session) {
        return new ChatSessionDto(
            session.getId().toString(),
            session.getTitle(),
            session.getSummary(),
            session.getUpdatedAt(),
            session.getMessages().size()
        );
    }

    private ChatMessageDto toChatMessageDto(ChatMessage message) {
        return new ChatMessageDto(
            message.getId().toString(),
            message.getRole(),
            message.getContent(),
            message.getToolCalls(),
            message.getGuardrails(),
            message.getMetadataJson(),
            message.getCreatedAt()
        );
    }

    private ChatAttachmentDto toChatAttachmentDto(ChatAttachment attachment) {
        return new ChatAttachmentDto(
            attachment.getId().toString(),
            attachment.getFileName(),
            attachment.getContentType(),
            attachment.getFileSize(),
            attachment.getDocumentId() == null ? null : attachment.getDocumentId().toString(),
            attachment.getProcessingStatus(),
            attachment.getUploadedAt()
        );
    }
}
