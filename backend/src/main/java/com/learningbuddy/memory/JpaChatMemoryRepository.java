package com.learningbuddy.memory;

import com.learningbuddy.models.ChatMessage;
import com.learningbuddy.models.ChatSession;
import com.learningbuddy.repositories.ChatMessageRepository;
import com.learningbuddy.repositories.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 官方 ChatMemoryRepository 的 JPA 实现。
 *
 * <p>把 ChatMemory 的 conversationId 映射到我们的 sessionId。
 * <p>存储层复用已有的 chat_message 表(与 SessionService 共享同一份数据)。
 *
 * <p>关键:Message ↔ ChatMessage 的双向映射。
 * <ul>
 *   <li>读:ChatMessage(role,content) → UserMessage/AssistantMessage/SystemMessage</li>
 *   <li>写:Message → ChatMessage(role,content),增量保存(不删旧,保留完整历史用于摘要/向量)</li>
 * </ul>
 *
 * <p>embedding 触发:保存后发 {@link MessageSavedEvent},由 SemanticMemoryService 监听处理,
 * 避免直接依赖 Service 层导致循环依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> findConversationIds() {
        return sessionRepository.findAll().stream()
                .map(s -> s.getId().toString())
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Long sessionId = parseSessionId(conversationId);
        if (sessionId == null) return List.of();
        List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        List<Message> result = new ArrayList<>(msgs.size() + 1);

        // 如果会话有摘要,作为第一条 SystemMessage 注入(早期对话的压缩记忆)
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getSummary() != null && !session.getSummary().isBlank()) {
                result.add(new SystemMessage("之前对话的摘要:\n" + session.getSummary()));
            }
        });

        // 只返回 summaryUpToId 之后的消息(之前的已压缩进摘要,避免重复)
        Long summaryUpToId = sessionRepository.findById(sessionId)
                .map(ChatSession::getSummaryUpToId).orElse(null);

        for (ChatMessage m : msgs) {
            if (summaryUpToId != null && m.getId() <= summaryUpToId) continue;  // 已摘要,跳过
            Message mapped = toMessage(m);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }

    /**
     * 全量保存(官方语义:覆盖该会话所有消息)。
     *
     * <p>实现策略:增量保存 —— 只追加新消息,不删旧的。
     * 理由:我们需要保留完整历史(给摘要压缩和向量检索用),窗口截断由
     * MessageWindowChatMemory / TokenAwareChatMemory 在内存层做,持久化层保留全量。
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Long sessionId = parseSessionId(conversationId);
        if (sessionId == null) return;

        // 读出现有消息,找出哪些是新的(按 content 去重)
        List<ChatMessage> existing = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        var existingContents = new java.util.HashSet<String>();
        for (ChatMessage m : existing) {
            existingContents.add(m.getRole() + "|" + m.getContent());
        }

        ChatSession sessionRef = ChatSession.builder().id(sessionId).build();
        int added = 0;
        for (Message msg : messages) {
            String role = fromMessage(msg);
            String content = msg.getText();
            if (content == null) continue;
            String key = role + "|" + content;
            if (existingContents.contains(key)) continue;   // 已存在,跳过
            ChatMessage saved = messageRepository.save(ChatMessage.builder()
                    .session(sessionRef)
                    .role(role)
                    .content(content)
                    .agentKind("ASSISTANT".equals(role) ? "TUTOR" : null)
                    .build());
            existingContents.add(key);
            added++;
            // 发事件触发异步 embedding(供跨会话语义检索),避免直接依赖 Service 层
            if (content.length() >= 4) {   // 太短的不 embed
                eventPublisher.publishEvent(new MessageSavedEvent(saved.getId(), content));
            }
        }
        if (added > 0) {
            log.debug("ChatMemory saveAll: session={} added={}/{}", sessionId, added, messages.size());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Long sessionId = parseSessionId(conversationId);
        if (sessionId == null) return;
        List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        messageRepository.deleteAll(msgs);
    }

    /* ===================== 映射 ===================== */

    private static Message toMessage(ChatMessage m) {
        String content = m.getContent();
        return switch (m.getRole() == null ? "USER" : m.getRole().toUpperCase()) {
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    private static String fromMessage(Message msg) {
        return switch (msg.getMessageType()) {
            case ASSISTANT -> "ASSISTANT";
            case SYSTEM -> "SYSTEM";
            default -> "USER";
        };
    }

    private static Long parseSessionId(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            log.warn("invalid conversationId (not a sessionId): {}", conversationId);
            return null;
        }
    }
}
