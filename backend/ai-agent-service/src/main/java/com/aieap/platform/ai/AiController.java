package com.aieap.platform.ai;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
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
    private final ConcurrentHashMap<String, ChatConversation> conversations = new ConcurrentHashMap<>();
    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("msg-1", "assistant", "Hello. I can summarize documents, extract tasks, and draft reports.", OffsetDateTime.now().minusMinutes(20)));
        conversations.put("chat-1", new ChatConversation("chat-1", "Morning Ops Review", OffsetDateTime.now().minusMinutes(20), messages));
    }

    @PostMapping("/chat")
    public ApiEnvelope<ChatReply> chat(@Valid @RequestBody ChatRequest chatRequest, HttpServletRequest request) {
        String chatId = chatRequest.chatId() == null || chatRequest.chatId().isBlank() ? UUID.randomUUID().toString() : chatRequest.chatId();
        ChatConversation conversation = conversations.computeIfAbsent(chatId, id -> new ChatConversation(id, chatRequest.prompt().substring(0, Math.min(chatRequest.prompt().length(), 24)), OffsetDateTime.now(), new ArrayList<>()));
        conversation.messages().add(new ChatMessage(UUID.randomUUID().toString(), "user", chatRequest.prompt(), OffsetDateTime.now()));

        AiChatService.ChatResult chatResult = aiChatService.answer(
            chatRequest.mode(),
            chatRequest.prompt(),
            chatRequest.attachments(),
            conversation.messages()
        );

        String answer = chatResult.content();
        ChatMessage assistantMessage = new ChatMessage(UUID.randomUUID().toString(), "assistant", answer, OffsetDateTime.now());
        conversation.messages().add(assistantMessage);

        return ResponseFactory.success(request, new ChatReply(chatId, assistantMessage, chatResult.guardrails()));
    }

    @GetMapping("/chats")
    public ApiEnvelope<List<ChatSummary>> chats(HttpServletRequest request) {
        List<ChatSummary> summaries = conversations.values().stream()
            .map(conversation -> new ChatSummary(conversation.id(), conversation.title(), conversation.updatedAt(), conversation.messages().size()))
            .toList();
        return ResponseFactory.success(request, summaries);
    }

    @GetMapping("/chats/{id}/messages")
    public ApiEnvelope<List<ChatMessage>> messages(@PathVariable String id, HttpServletRequest request) {
        return ResponseFactory.success(request, conversation(id).messages());
    }

    @PostMapping("/chats/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope<AttachmentReceipt> attach(
        @PathVariable String id,
        @Valid @RequestBody AttachmentRequest attachmentRequest,
        HttpServletRequest request
    ) {
        conversation(id);
        return ResponseFactory.success(request, new AttachmentReceipt(id, attachmentRequest.fileName(), attachmentRequest.contentType(), attachmentRequest.size(), "QUEUED"));
    }

    private ChatConversation conversation(String id) {
        ChatConversation conversation = conversations.get(id);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        return conversation;
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

    public record AttachmentRequest(@NotBlank String fileName, @NotBlank String contentType, long size, Map<String, String> metadata) {
    }

    public record AttachmentReceipt(String chatId, String fileName, String contentType, long size, String status) {
    }
}