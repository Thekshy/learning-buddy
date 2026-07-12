package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 聊天消息(对应 chat_message 表)
 *
 * <p>记忆系统的数据载体:Orchestrator 注入 prompt 的历史就来自这里。
 * <p>删会话级联删消息(ON DELETE CASCADE)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    /** USER / ASSISTANT / SYSTEM */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 该消息由哪个 Agent 产出(可空) */
    @Column(name = "agent_kind", length = 32)
    private String agentKind;

    /** token 数 / 调用耗时 / RAG 命中数 等 JSON */
    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    /** 该消息的向量(JSON 字符串形式,供语义检索用;异步填充) */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
