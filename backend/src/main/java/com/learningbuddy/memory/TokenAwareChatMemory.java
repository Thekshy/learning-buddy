package com.learningbuddy.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持 Token 预算的 ChatMemory 装饰器。
 *
 * <p>包装 {@link MessageWindowChatMemory}(它按消息条数截断),
 * 在此基础上额外按 token 总量裁剪:从最旧的消息开始丢弃,直到总 token 降到预算内。
 *
 * <p>关键规则:
 * <ul>
 *   <li>{@code SystemMessage} 永不丢弃(角色定义、掌握度画像、摘要必须保留)</li>
 *   <li>保留最新的消息(从头部裁旧的)</li>
 *   <li>Token 用 {@link JTokkitTokenCountEstimator} 估算(cl100k_base 编码,GPT 系列通用)</li>
 * </ul>
 */
@Slf4j
public class TokenAwareChatMemory implements ChatMemory {

    /** 注入历史时 token 预算上限(留余地给 system prompt + 工具 schema + 输出) */
    private static final int MAX_HISTORY_TOKENS = 3000;

    private final MessageWindowChatMemory delegate;
    private final TokenCountEstimator tokenCounter = new JTokkitTokenCountEstimator();

    public TokenAwareChatMemory(MessageWindowChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
    }

    /**
     * 取记忆:先从 delegate 拿条数窗口内的消息,再按 token 裁剪。
     * SystemMessage 始终保留,从最旧的非系统消息开始裁。
     */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> raw = delegate.get(conversationId);
        return trimByTokens(raw, MAX_HISTORY_TOKENS);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    /** 按 token 预算裁剪:SystemMessage 全留,其余从最旧的开始丢 */
    List<Message> trimByTokens(List<Message> messages, int maxTokens) {
        if (messages.isEmpty()) return messages;

        List<Message> systemMsgs = new ArrayList<>();
        List<Message> dialogMsgs = new ArrayList<>();
        for (Message m : messages) {
            if (m instanceof SystemMessage) systemMsgs.add(m);
            else dialogMsgs.add(m);
        }

        int systemTokens = sumTokens(systemMsgs);
        int budget = maxTokens - systemTokens;
        if (budget <= 0) {
            // system 已经超预算(极端情况),只留 system
            log.warn("system messages alone exceed token budget: {} tokens", systemTokens);
            return systemMsgs;
        }

        // 从最新的往回累加(dialogMsgs 是时间正序,最新在尾部)
        List<Message> kept = new ArrayList<>();
        int used = 0;
        for (int i = dialogMsgs.size() - 1; i >= 0; i--) {
            Message m = dialogMsgs.get(i);
            int t = tokenCounter.estimate(m.getText() == null ? "" : m.getText());
            if (used + t > budget) break;
            used += t;
            kept.add(0, m);   // 保持正序
        }

        List<Message> result = new ArrayList<>(systemMsgs.size() + kept.size());
        result.addAll(systemMsgs);
        result.addAll(kept);

        int dropped = dialogMsgs.size() - kept.size();
        if (dropped > 0) {
            log.debug("token trim: {} dialog msgs kept, {} dropped (budget={}, used={})",
                    kept.size(), dropped, budget, used);
        }
        return result;
    }

    private int sumTokens(List<Message> msgs) {
        int sum = 0;
        for (Message m : msgs) {
            sum += tokenCounter.estimate(m.getText() == null ? "" : m.getText());
        }
        return sum;
    }
}
