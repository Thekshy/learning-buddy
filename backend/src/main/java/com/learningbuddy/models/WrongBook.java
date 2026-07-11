package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 错题本(对应 wrong_book 表)
 *
 * <p>UNIQUE(user_id, question_id):同一用户同一题只一条记录,反复错则更新 master_level。
 * <p>master_level:0=未掌握(刚进错题本)、1=部分掌握、2=掌握(下次做对后升级)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wrong_book",
        uniqueConstraints = @UniqueConstraint(name = "uk_wrong_user_question",
                columnNames = {"user_id", "question_id"}))
public class WrongBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** 0=未掌握, 1=部分, 2=掌握 */
    @Column(name = "master_level", nullable = false)
    @Builder.Default
    private Integer masterLevel = 0;

    /** 最近一次作答(逻辑外键,schema 未建约束) */
    @Column(name = "last_attempt_id")
    private Long lastAttemptId;

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
