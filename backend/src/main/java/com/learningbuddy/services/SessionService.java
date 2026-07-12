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
 * 会话服务
 *
 * <p>管理 ChatSession 生命周期 + ChatMessage 查询。
 * <p>记忆注入已由 MessageChatMemoryAdvisor 自动完成,本类只负责会话 CRUD 和历史查询接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

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

    // 注:recentHistoryAsText 已移除 —— 记忆现在由 MessageChatMemoryAdvisor 自动注入,
    // 不再手动拼历史文本。SessionService 专注于会话 CRUD + 消息查询(给前端历史接口用)。
}
