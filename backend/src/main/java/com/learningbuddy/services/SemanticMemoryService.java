package com.learningbuddy.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningbuddy.core.LlmClient;
import com.learningbuddy.memory.MessageSavedEvent;
import com.learningbuddy.models.ChatMessage;
import com.learningbuddy.repositories.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 语义记忆服务 —— 跨会话的长期记忆(向量检索)
 *
 * <p>与 ChatMemory 的"短期窗口记忆"互补:
 * <ul>
 *   <li>ChatMemory:只保留最近 N 条(窗口内),刷新即丢窗口外的</li>
 *   <li>语义记忆:给所有历史消息算 embedding,检索时按相似度跨会话召回</li>
 * </ul>
 *
 * <p>两个职责:
 * <ol>
 *   <li>{@link #onMessageSaved} — 监听消息落库事件,异步算向量存入 chat_message.embedding</li>
 *   <li>{@link #retrieveRelated} — 给当前输入找语义相关的历史片段,拼进 system prompt</li>
 * </ol>
 *
 * <p>通过 {@link EventListener} 接收事件,与 JpaChatMemoryRepository 解耦(避免循环依赖)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMemoryService {

    /** 检索 topK 条相关历史 */
    private static final int TOP_K = 3;
    /** 相似度阈值,低于此不召回(避免噪音) */
    private static final double MIN_SCORE = 0.65;

    private final LlmClient llm;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper json = new ObjectMapper();

    /** 监听消息落库事件,异步算 embedding 并存库 */
    @Async
    @EventListener
    public void onMessageSaved(MessageSavedEvent event) {
        embedMessage(event.messageId(), event.content());
    }

    private void embedMessage(Long messageId, String content) {
        try {
            float[] vec = llm.embed(content);
            if (vec == null) {
                // embeddings 已关闭:不写零向量,保持 embedding 列为 null(检索时会跳过)
                log.debug("embeddings disabled, skip storing embedding for msg {}", messageId);
                return;
            }
            // float[] → double[](Jackson 默认支持 double[] 序列化)
            double[] dbl = new double[vec.length];
            for (int i = 0; i < vec.length; i++) dbl[i] = vec[i];
            String embeddingJson = json.writeValueAsString(dbl);
            messageRepository.findById(messageId).ifPresent(m -> {
                m.setEmbedding(embeddingJson);
                messageRepository.save(m);
            });
            log.debug("embedded msg {}", messageId);
        } catch (Exception e) {
            log.warn("embedAsync failed for msg {}: {}", messageId, e.getMessage());
        }
    }

    /**
     * 检索与当前输入语义相关的历史片段(跨会话)。
     *
     * @return 拼好的 system prompt 片段(如 "你之前还和用户聊过这些相关内容:..."),
     *         无相关内容返回 null
     */
    public String retrieveRelated(Long userId, String query) {
        if (query == null || query.isBlank()) return null;
        List<ChatMessage> candidates = messageRepository.findEmbeddedByUserId(userId);
        if (candidates.isEmpty()) return null;

        float[] queryVec;
        try {
            queryVec = llm.embed(query);
        } catch (Exception e) {
            log.warn("embed query failed: {}", e.getMessage());
            return null;
        }
        if (queryVec == null) return null;

        // 算余弦相似度,取 topK
        List<Hit> hits = new ArrayList<>();
        for (ChatMessage m : candidates) {
            double[] mv = parseEmbedding(m.getEmbedding());
            if (mv == null) continue;
            double score = cosine(queryVec, mv);
            if (score >= MIN_SCORE) {
                hits.add(new Hit(m, score));
            }
        }
        if (hits.isEmpty()) return null;
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        List<Hit> top = hits.subList(0, Math.min(TOP_K, hits.size()));

        StringBuilder sb = new StringBuilder("你之前还和用户聊过以下相关内容(可参考但不必复述):\n");
        for (int i = 0; i < top.size(); i++) {
            ChatMessage m = top.get(i).msg();
            String snippet = m.getContent();
            if (snippet.length() > 150) snippet = snippet.substring(0, 150) + "...";
            sb.append(String.format("[%d] %s: %s\n", i + 1,
                    "ASSISTANT".equals(m.getRole()) ? "你曾说" : "用户曾说", snippet));
        }
        log.debug("semantic memory: {} hits for user {}", top.size(), userId);
        return sb.toString();
    }

    private double[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return this.json.readValue(json, double[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private static double cosine(float[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private record Hit(ChatMessage msg, double score) {}
}
