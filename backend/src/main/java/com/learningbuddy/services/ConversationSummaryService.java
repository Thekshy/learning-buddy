package com.learningbuddy.services;

import com.learningbuddy.core.LlmClient;
import com.learningbuddy.models.ChatMessage;
import com.learningbuddy.models.ChatSession;
import com.learningbuddy.repositories.ChatMessageRepository;
import com.learningbuddy.repositories.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对话摘要压缩服务
 *
 * <p>当会话消息超过阈值(20 条)时,把已摘要窗口之外的旧消息压缩成一段摘要,
 * 存到 chat_session.summary。这样 LLM 即使窗口装不下全部历史,也能通过摘要
 * 记住早期对话的主线。
 *
 * <p>触发:ChatController 在每次请求后异步调用(@Async,不阻塞响应)。
 * <p>注入:TokenAwareChatMemory 在 get() 时,如果有 summary,把它作为
 * 第一条 SystemMessage 注入("之前对话的摘要:...")。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    /** 超过这么多条消息,触发摘要(与 MessageWindowChatMemory 的 maxMessages 对齐) */
    private static final int SUMMARY_THRESHOLD = 20;

    /** 每次摘要保留最近多少条不压缩(这些留在窗口里原文) */
    private static final int KEEP_RECENT = 12;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LlmClient llm;

    /**
     * 如果需要,异步生成/更新会话摘要。
     * <p>失败只打日志,不影响主请求。
     */
    @Async
    public void summarizeIfNeeded(Long sessionId) {
        try {
            doSummarize(sessionId);
        } catch (Exception e) {
            log.warn("summarizeIfNeeded failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Transactional
    protected void doSummarize(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;

        List<ChatMessage> all = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        if (all.size() < SUMMARY_THRESHOLD) return;   // 还没到阈值

        // 找出"已摘要覆盖之外 + 保留窗口之外"的待摘要消息
        long summaryUpTo = session.getSummaryUpToId() != null ? session.getSummaryUpToId() : 0L;
        // 保留最近 KEEP_RECENT 条不压缩
        long keepAfterId = all.get(all.size() - KEEP_RECENT).getId();

        // 待摘要的消息:summaryUpTo < id <= keepAfterId
        List<ChatMessage> toSummarize = all.stream()
                .filter(m -> m.getId() > summaryUpTo && m.getId() <= keepAfterId)
                .toList();
        if (toSummarize.isEmpty()) return;

        // 拼待摘要文本(旧 summary + 新消息)
        StringBuilder input = new StringBuilder();
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            input.append("已有摘要:\n").append(session.getSummary()).append("\n\n新增对话:\n");
        }
        for (ChatMessage m : toSummarize) {
            input.append(m.getRole()).append(": ").append(abbreviate(m.getContent(), 300)).append("\n");
        }

        String system = """
                你是对话摘要助手。请把以下学习辅导对话压缩成一段 150 字以内的中文摘要,
                保留关键信息:用户在学什么、掌握了哪些、还有什么问题、达成了什么共识。
                只输出摘要正文,不要任何额外说明。""";
        String newSummary = llm.chat(system, input.toString()).content();
        if (newSummary == null || newSummary.isBlank() || "MOCK".equals(newSummary)) {
            log.debug("summary LLM returned empty, skip");
            return;
        }

        session.setSummary(newSummary.trim());
        session.setSummaryUpToId(keepAfterId);
        sessionRepository.save(session);
        log.info("会话 {} 摘要已更新:覆盖到 msg {}, 摘要 {} 字",
                sessionId, keepAfterId, newSummary.length());
    }

    private static String abbreviate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
