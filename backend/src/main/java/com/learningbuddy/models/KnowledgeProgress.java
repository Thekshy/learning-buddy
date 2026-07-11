package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 知识掌握度(对应 knowledge_progress 表)
 *
 * <p>UNIQUE(user_id, knowledge_node_id):每个用户每个知识点一条进度。
 * <p>mastery = correct_count / attempt_count * 100(取整),是雷达图的纵轴。
 * <p>每次判分后重算;generateQuiz 出题时读它做难度自适应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_user_node",
                columnNames = {"user_id", "knowledge_node_id"}))
public class KnowledgeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_node_id", nullable = false)
    private KnowledgeNode knowledgeNode;

    /** 0-100 */
    @Column(nullable = false)
    @Builder.Default
    private Integer mastery = 0;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "correct_count", nullable = false)
    @Builder.Default
    private Integer correctCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** 重算掌握度 = correct_count / attempt_count * 100(向下取整) */
    public void recompute() {
        this.mastery = (attemptCount == null || attemptCount == 0)
                ? 0
                : (int) ((long) correctCount * 100 / attemptCount);
    }
}
