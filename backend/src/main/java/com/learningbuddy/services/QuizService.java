package com.learningbuddy.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningbuddy.models.*;
import com.learningbuddy.repositories.*;
import com.learningbuddy.tools.LearningTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quiz 闭环服务
 *
 * <p>三大职责:
 * <ol>
 *   <li>{@link #saveGeneratedQuiz} — 把 generateQuiz 工具产出的题目落库,生成 quizId</li>
 *   <li>{@link #grade} — 判分:逐题比对 → 写 attempt → 写错题本 → 更新掌握度</li>
 *   <li>{@link #userStats} — 读真实做题统计,供 reviewProgress 工具用</li>
 * </ol>
 *
 * <p>判分规则:CHOICE 精确匹配;FILL trim 后忽略大小写匹配。
 * <p>掌握度:mastery = correct_count / attempt_count * 100(向下取整)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final WrongBookRepository wrongBookRepository;
    private final KnowledgeProgressRepository progressRepository;
    private final KnowledgeNodeRepository nodeRepository;
    private final AppUserRepository userRepository;
    private final ObjectMapper json = new ObjectMapper();

    /* ===================== 1. 出题落库 ===================== */

    /**
     * 把工具产出的 QuizResult 落库,生成 quizId + 每题 questionId。
     * <p>前端拿到这些 id 才能提交答案。
     *
     * @param subject   学科(用于挂靠知识点;模糊匹配,找不到也不阻塞)
     * @param nodeTitle 知识点标题(同上)
     * @param questions 工具产出的题目列表
     * @return 带 id 的题目列表 + quizId
     */
    @Transactional
    public SavedQuiz saveGeneratedQuiz(Long userId, String subject, String nodeTitle,
                                       List<LearningTools.Question> questions) {
        AppUser userRef = AppUser.builder().id(userId).build();

        // 模糊匹配知识点(找不到就 null,掌握度更新会跳过)
        KnowledgeNode node = null;
        if (nodeTitle != null && !nodeTitle.isBlank()) {
            node = nodeRepository.findFirstByTitleContaining(nodeTitle).orElse(null);
        }

        Quiz quiz = Quiz.builder()
                .user(userRef)
                .knowledgeNode(node)
                .quizType("PRACTICE")
                .title(nodeTitle != null ? nodeTitle + " 练习" : "练习")
                .build();
        quiz = quizRepository.save(quiz);

        List<SavedQuestion> saved = new ArrayList<>();
        int order = 0;
        for (LearningTools.Question q : questions) {
            Question entity = Question.builder()
                    .quiz(quiz)
                    .qType(q.type() != null ? q.type() : "CHOICE")
                    .difficulty(2)
                    .stem(q.stem())
                    .optionsJson(toJson(q.options()))
                    .answerJson(toJson(q.answer()))
                    .analysis(q.analysis())
                    .sortOrder(order++)
                    .build();
            entity = questionRepository.save(entity);
            saved.add(new SavedQuestion(
                    entity.getId(),
                    entity.getQType(),
                    entity.getStem(),
                    q.options(),
                    entity.getAnswerJson(),   // 原始答案文本
                    entity.getAnalysis()
            ));
        }
        log.info("Quiz saved: quizId={} questions={} user={}", quiz.getId(), saved.size(), userId);
        return new SavedQuiz(quiz.getId(), saved);
    }

    /* ===================== 2. 判分 ===================== */

    /**
     * 判分:逐题比对 → 写 attempt → 写错题本 → 更新掌握度。
     *
     * @return 每题判分结果 + 汇总统计
     */
    @Transactional
    public GradeResult grade(Long userId, Long quizId, List<Answer> answers) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("quiz not found: " + quizId));
        List<Question> questions = questionRepository.findByQuizIdOrderBySortOrderAsc(quizId);
        Map<Long, Question> qById = new java.util.HashMap<>();
        for (Question q : questions) qById.put(q.getId(), q);

        AppUser userRef = AppUser.builder().id(userId).build();
        List<GradeItem> graded = new ArrayList<>();
        int correct = 0;

        for (Answer a : answers) {
            Question q = qById.get(a.questionId());
            if (q == null) {
                log.warn("grade: question not found {}", a.questionId());
                continue;
            }
            boolean isCorrect = checkAnswer(q, a.userAnswer());
            if (isCorrect) correct++;

            // 写 attempt
            attemptRepository.save(Attempt.builder()
                    .user(userRef)
                    .quiz(quiz)
                    .question(q)
                    .userAnswer(a.userAnswer())
                    .isCorrect(isCorrect)
                    .score(isCorrect ? q.getScore() : 0)
                    .elapsedMs(a.elapsedMs())
                    .build());

            // 错题本:做错就进(或更新);做对则升级 masterLevel
            updateWrongBook(userId, q.getId(), isCorrect);

            // 掌握度更新(若 quiz 挂靠了知识点)
            if (quiz.getKnowledgeNode() != null) {
                updateProgress(userId, quiz.getKnowledgeNode().getId(), isCorrect);
            }

            graded.add(new GradeItem(
                    q.getId(), isCorrect, a.userAnswer(),
                    q.getAnswerJson(), q.getAnalysis()
            ));
        }

        int total = answers.size();
        double accuracy = total == 0 ? 0 : (correct * 100.0 / total);
        log.info("Quiz graded: quizId={} user={} {}/{} ({}%)", quizId, userId, correct, total, (int) accuracy);
        return new GradeResult(quizId, graded,
                new Summary(total, correct, Math.round(accuracy * 10) / 10.0));
    }

    /* ===================== 3. 用户做题统计(供 reviewProgress)===================== */

    @Transactional(readOnly = true)
    public UserStats userStats(Long userId) {
        long total = attemptRepository.countByUserId(userId);
        long correct = attemptRepository.countByUserIdAndIsCorrectTrue(userId);
        long wrongCount = wrongBookRepository
                .findByUserIdAndMasterLevelLessThanOrderByUpdatedAtDesc(userId, 2).size();
        return new UserStats(total, correct, wrongCount);
    }

    /* ===================== 内部 ===================== */

    private boolean checkAnswer(Question q, String userAnswer) {
        if (userAnswer == null) return false;
        String correct = fromJson(q.getAnswerJson(), String.class);
        if (correct == null) return false;
        if ("FILL".equalsIgnoreCase(q.getQType())) {
            return userAnswer.trim().equalsIgnoreCase(correct.trim());
        }
        // CHOICE:精确匹配(选项文本)
        return userAnswer.trim().equals(correct.trim());
    }

    private void updateWrongBook(Long userId, Long questionId, boolean isCorrect) {
        Optional<WrongBook> existing = wrongBookRepository.findByUserIdAndQuestionId(userId, questionId);
        if (isCorrect) {
            // 做对:若在错题本里,升级 master_level 到 2(掌握)
            existing.ifPresent(wb -> {
                wb.setMasterLevel(2);
                wrongBookRepository.save(wb);
            });
        } else {
            // 做错:进错题本或保持未掌握
            WrongBook wb = existing.orElseGet(() -> WrongBook.builder()
                    .user(AppUser.builder().id(userId).build())
                    .question(Question.builder().id(questionId).build())
                    .masterLevel(0)
                    .build());
            wb.setMasterLevel(0);
            wrongBookRepository.save(wb);
        }
    }

    private void updateProgress(Long userId, Long nodeId, boolean isCorrect) {
        KnowledgeProgress kp = progressRepository
                .findByUserIdAndKnowledgeNodeId(userId, nodeId)
                .orElseGet(() -> KnowledgeProgress.builder()
                        .user(AppUser.builder().id(userId).build())
                        .knowledgeNode(KnowledgeNode.builder().id(nodeId).build())
                        .build());
        kp.setAttemptCount((kp.getAttemptCount() == null ? 0 : kp.getAttemptCount()) + 1);
        if (isCorrect) {
            kp.setCorrectCount((kp.getCorrectCount() == null ? 0 : kp.getCorrectCount()) + 1);
        }
        kp.recompute();
        progressRepository.save(kp);
    }

    private String toJson(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (Exception e) {
            return obj == null ? null : String.valueOf(obj);
        }
    }

    private <T> T fromJson(String s, Class<T> type) {
        if (s == null) return null;
        try {
            return json.readValue(s, type);
        } catch (Exception e) {
            // 可能是裸字符串而非 JSON(如 "def"),直接返回
            try {
                return type.cast(s.replaceAll("^\"|\"$", ""));
            } catch (ClassCastException ex) {
                return null;
            }
        }
    }

    /* ===================== DTO ===================== */

    public record Answer(Long questionId, String userAnswer, Integer elapsedMs) {}

    public record SavedQuiz(Long quizId, List<SavedQuestion> questions) {}

    /** 落库后的题目(带 questionId,供前端提交答案用) */
    public record SavedQuestion(
            Long questionId, String type, String stem,
            List<String> options, String answer, String analysis) {}

    public record GradeResult(Long quizId, List<GradeItem> graded, Summary summary) {}
    public record GradeItem(Long questionId, boolean correct, String userAnswer,
                            String correctAnswer, String analysis) {}
    public record Summary(int total, int correct, double accuracy) {}

    public record UserStats(long total, long correct, long wrongCount) {}
}
