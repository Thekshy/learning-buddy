package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 聊天会话(对应 chat_session 表)
 *
 * <p>一个用户可有多个会话;会话下的消息级联删除(见 ChatMessage)。
 * <p>记忆系统的组织单元:Orchestrator 注入的历史 = 该会话下最近 N 条消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 255)
    @Builder.Default
    private String title = "新会话";

    /** 当前主要 Agent:PLANNER / QUIZ / TUTOR / RECOMMENDER / REVIEWER */
    @Column(name = "agent_kind", length = 32)
    @Builder.Default
    private String agentKind = "TUTOR";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
