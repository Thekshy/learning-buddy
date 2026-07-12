package com.learningbuddy.services;

import com.learningbuddy.core.LlmClient;
import com.learningbuddy.models.ChatSession;
import com.learningbuddy.repositories.ChatMessageRepository;
import com.learningbuddy.repositories.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话标题自动生成服务
 *
 * <p>会话第一条用户消息后,异步调 LLM 生成一个 10 字以内的标题,
 * 更新到 chat_session.title。这样前端会话列表能显示有意义的标题。
 *
 * <p>触发:ChatController 在请求后异步调用。只在标题还是默认"新会话"时生成,避免覆盖用户自定义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LlmClient llm;

    @Async
    public void generateTitleIfNeeded(Long sessionId) {
        try {
            doGenerate(sessionId);
        } catch (Exception e) {
            log.warn("generateTitleIfNeeded failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Transactional
    protected void doGenerate(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;
        // 只在标题还是默认值时生成(避免覆盖)
        if (session.getTitle() != null && !"新会话".equals(session.getTitle())) return;

        var msgs = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        if (msgs.size() < 2) return;   // 至少要有一问一答

        // 取第一条用户消息作为标题生成依据
        String firstUser = msgs.stream()
                .filter(m -> "USER".equals(m.getRole()))
                .map(m -> m.getContent())
                .findFirst()
                .orElse(null);
        if (firstUser == null || firstUser.isBlank()) return;
        if (firstUser.length() > 100) firstUser = firstUser.substring(0, 100);

        String system = "请根据用户的学习请求,生成一个 10 字以内的中文标题,直接输出标题文字,不要引号、不要标点、不要解释。";
        String title = llm.chat(system, firstUser).content();
        if (title == null || title.isBlank() || "MOCK".equals(title)) return;

        title = title.trim().replaceAll("[\"'\\[\\]「」]", "");
        title = title.replace("\u201c", "").replace("\u201d", "")   // 中文双引号
                     .replace("\u2018", "").replace("\u2019", "");   // 中文单引号
        if (title.length() > 20) title = title.substring(0, 20);

        session.setTitle(title);
        sessionRepository.save(session);
        log.info("会话 {} 标题已生成: {}", sessionId, title);
    }
}
