package com.learningbuddy.repositories;

import com.learningbuddy.models.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 某用户所有会话的所有消息(跨会话语义检索用,通过 session.user 联结) */
    @org.springframework.data.jpa.repository.Query("select m from ChatMessage m where m.session.user.id = ?1 and m.embedding is not null")
    List<ChatMessage> findEmbeddedByUserId(Long userId);
}
