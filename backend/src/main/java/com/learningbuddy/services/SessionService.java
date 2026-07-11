package com.learningbuddy.services;

import com.learningbuddy.models.ChatMessage;
import com.learningbuddy.models.ChatSession;
import com.learningbuddy.models.AppUser;
import com.learningbuddy.repositories.ChatMessageRepository;
import com.learningbuddy.repositories.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话与记忆服务
 *
 * <p>记忆系统的核心:管理 ChatSession 生命周期 + 读写 ChatMessage。
 * <p>记忆注入策略:取当前会话最近 N 条消息,拼成对话历史文本喂给 Orchestrator。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /** 记忆窗口:注入最近多少条消息(控制 token,默认 10 条 ≈ 5 轮对话) */
    private static final int MEMORY_WINDOW = 10;

    /**
     * 取或建会话。
     * <p>sessionId 为空时为用户建一个默认会话(最小改动支持单会话;接口已预留多会话)。
     */
    @Transactional
    public ChatSession getOrCreateSession(Long userId, Long sessionId) {
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .filter(s -> s.getUser().getId().equals(userId))
                    .orElseGet(() -> createSession(userId, "新会话"));
        }
        // 无 sessionId:取该用户最近一个会话,没有就建
        List<ChatSession> recent = sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (!recent.isEmpty()) {
            return recent.get(0);
        }
        return createSession(userId, "新会话");
    }

    @Transactional
    public ChatSession createSession(Long userId, String title) {
        AppUser userRef = AppUser.builder().id(userId).build();
        ChatSession session = ChatSession.builder()
                .user(userRef)
                .title(title != null ? title : "新会话")
                .agentKind("TUTOR")
                .build();
        return sessionRepository.save(session);
    }

    /** 列出用户所有会话 */
    @Transactional(readOnly = true)
    public List<ChatSession> listSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** 拉取某会话全部消息(前端历史展示) */
    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId);
    }

    /** 存一条消息 */
    @Transactional
    public ChatMessage saveMessage(Long sessionId, String role, String content, String agentKind) {
        ChatSession sessionRef = ChatSession.builder().id(sessionId).build();
        ChatMessage msg = ChatMessage.builder()
                .session(sessionRef)
                .role(role)
                .content(content)
                .agentKind(agentKind)
                .build();
        return messageRepository.save(msg);
    }

    /**
     * 取最近记忆,拼成对话历史文本(供 Orchestrator 注入 prompt)。
     * <p>格式:每行一条 "USER: xxx" / "ASSISTANT: xxx"。
     * <p>不含当前轮的用户输入(当前轮由 user message 单独传)。
     */
    @Transactional(readOnly = true)
    public String recentHistoryAsText(Long sessionId) {
        List<ChatMessage> recent = messageRepository.findRecent(sessionId, MEMORY_WINDOW);
        if (recent.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : recent) {
            String role = "ASSISTANT".equals(m.getRole()) ? "AI" : m.getRole();
            sb.append(role).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }
}
