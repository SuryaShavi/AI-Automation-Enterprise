package com.aieap.platform.ai.repository;

import com.aieap.platform.ai.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    
    @Query("SELECT m FROM ChatMessage m WHERE m.chatSession.id = :chatSessionId ORDER BY m.createdAt ASC")
    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(@Param("chatSessionId") UUID chatSessionId);

    Optional<ChatMessage> findFirstByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId);
}
