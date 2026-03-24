package com.aieap.platform.ai.repository;

import com.aieap.platform.ai.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    
@Query("SELECT c FROM ChatSession c WHERE c.userId = :userId ORDER BY c.updatedAt DESC")
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(@Param("userId") UUID userId);
    
@Query("SELECT c FROM ChatSession c WHERE c.userId = :userId AND c.id = :chatId")
    Optional<ChatSession> findByIdAndUserId(@Param("chatId") UUID chatId, @Param("userId") UUID userId);
    
@Query(value = "SELECT c FROM chat_sessions c WHERE c.user_id = :userId ORDER BY c.created_at DESC LIMIT :limit", nativeQuery = true)
    List<ChatSession> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}
