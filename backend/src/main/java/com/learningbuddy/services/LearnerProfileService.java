package com.learningbuddy.services;

import com.learningbuddy.models.KnowledgeProgress;
import com.learningbuddy.repositories.KnowledgeProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 学习者画像服务
 *
 * <p>从 knowledge_progress 表读用户各知识点的掌握度,生成一段"学习画像"文本,
 * 注入到 system prompt —— 让 LLM 知道用户哪里薄弱,出题/讲解能针对性调整。
 *
 * <p>这就根治了"LLM 不知道用户历史掌握情况,每次出题都是白板"的问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerProfileService {

    private final KnowledgeProgressRepository progressRepository;

    /**
     * 构建用户学习画像 system prompt 片段。
     *
     * @return 画像文本(如"用户学习画像:装饰器 30%(薄弱)..."),无数据返回 null
     */
    @Transactional(readOnly = true)
    public String buildProfile(Long userId) {
        if (userId == null) return null;
        List<KnowledgeProgress> all = progressRepository.findByUserId(userId);
        if (all.isEmpty()) return null;

        // 按 mastery 升序(薄弱的在前,最需要关注的优先)
        all.sort(Comparator.comparingInt(KnowledgeProgress::getMastery));

        StringBuilder sb = new StringBuilder("📚 用户学习画像(请据此调整教学策略,薄弱处多讲解/多出题):\n");
        for (KnowledgeProgress kp : all) {
            String nodeTitle = kp.getKnowledgeNode() != null ? kp.getKnowledgeNode().getTitle() : "未知知识点";
            int mastery = kp.getMastery();
            String level = mastery >= 80 ? "熟练" : mastery >= 50 ? "一般" : mastery >= 20 ? "薄弱" : "未入门";
            sb.append(String.format("- %s: 掌握度 %d%%(%s,做了 %d 题,对 %d 题)\n",
                    nodeTitle, mastery, level, kp.getAttemptCount(), kp.getCorrectCount()));
        }
        log.debug("built profile for user {}: {} nodes", userId, all.size());
        return sb.toString();
    }
}
