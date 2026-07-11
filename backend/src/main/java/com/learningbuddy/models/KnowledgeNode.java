package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识树节点(对应 knowledge_node 表)
 *
 * <p>自引用 parent_id 组成树;KnowledgeProgress / Quiz 挂靠此节点。
 * <p>掌握度(雷达图)的横轴就是各个 KnowledgeNode。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_node",
        uniqueConstraints = @UniqueConstraint(name = "uk_node_subject_code",
                columnNames = {"subject_id", "code"}))
public class KnowledgeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** 自引用父节点,根节点为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgeNode parent;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1-5 */
    @Column(nullable = false)
    @Builder.Default
    private Integer difficulty = 1;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
