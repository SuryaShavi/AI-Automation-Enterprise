package com.aieap.platform.ai.repository;

import com.aieap.platform.ai.domain.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {

    @Query("SELECT a FROM ChatAttachment a WHERE a.chatSession.id = :chatSessionId ORDER BY a.uploadedAt DESC")
    List<ChatAttachment> findByChatSessionIdOrderByUploadedAtDesc(@Param("chatSessionId") UUID chatSessionId);

    @Query("SELECT a FROM ChatAttachment a WHERE a.chatSession.id = :chatSessionId AND a.id IN :attachmentIds")
    List<ChatAttachment> findByChatSessionIdAndIdIn(@Param("chatSessionId") UUID chatSessionId, @Param("attachmentIds") List<UUID> attachmentIds);
}
