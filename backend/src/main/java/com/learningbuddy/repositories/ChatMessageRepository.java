package com.learningbuddy.repositories;

import com.learningbuddy.models.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 取某会话最近 limit 条消息(记忆注入用),按时间正序返回 */
    default List<ChatMessage> findRecent(Long sessionId, int limit) {
        List<ChatMessage> msgs = findBySessionIdOrderByIdDesc(sessionId, Pageable.ofSize(limit));
        java.util.Collections.reverse(msgs);   // 翻成正序
        return msgs;
    }

    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);
}
