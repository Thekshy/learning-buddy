package com.learningbuddy.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目(对应 question 表)
 *
 * <p>判分时比对 {@code userAnswer} 与 {@code answerJson}:
 * <ul>
 *   <li>CHOICE:精确匹配(选项文本)</li>
 *   <li>FILL:trim 后忽略大小写匹配</li>
 * </ul>
 *
 * <p>{@code optionsJson} / {@code answerJson} / {@code analysis}
 * 对应 LearningTools.Question 的 options / answer / analysis 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /** CHOICE / FILL / SHORT / CODE */
    @Column(name = "q_type", nullable = false, length = 16)
    private String qType;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    /** CHOICE 时为 JSON 数组(如 ["def","fun","define","function"]);FILL 时为 null */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** 标准答案(选择题是选项文本;填空题是答案字符串) */
    @Column(name = "answer_json", nullable = false, columnDefinition = "TEXT")
    private String answerJson;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 10;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
