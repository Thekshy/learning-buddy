package com.learningbuddy.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningbuddy.config.PropertiesConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM 客户端封装(Spring AI 之上的一层薄壳)
 *
 * <p>职责:
 * <ul>
 *   <li>统一聊天调用入口(支持结构化输出)</li>
 *   <li>异常时降级到 mock(便于离线演示)</li>
 *   <li>记录耗时 / token(供 Agent 调用链日志使用)</li>
 * </ul>
 *
 * <p>MiniMax M3 走 OpenAI 兼容协议,在 application.yml 配 base-url 即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final PropertiesConfig properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 简单聊天(纯文本) */
    public LlmResult chat(String systemPrompt, String userPrompt) {
        long t0 = System.currentTimeMillis();
        try {
            ChatResponse resp = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            String content = resp.getResult().getOutput().getText();
            long ms = System.currentTimeMillis() - t0;
            log.debug("LLM chat ok: {}ms, {} chars", ms, content.length());
            return new LlmResult(content, true, ms, extractUsage(resp));
        } catch (Exception e) {
            log.warn("LLM chat failed, fallback={}: {}", properties.llm().fallbackMock(), e.getMessage());
            return new LlmResult(mockAnswer(systemPrompt, userPrompt), false, System.currentTimeMillis() - t0, null);
        }
    }

    /** 结构化输出(JSON → 目标类型) */
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type) {
        LlmResult raw = chat(systemPrompt, userPrompt + "\n\n请严格用 JSON 格式输出,不要任何额外说明。");
        return parseJson(raw.content(), type);
    }

    /** 结构化输出(支持 List<T> 等泛型,用 TypeReference) */
    public <T> T chatJson(String systemPrompt, String userPrompt, TypeReference<T> typeRef) {
        LlmResult raw = chat(systemPrompt, userPrompt + "\n\n请严格用 JSON 格式输出,不要任何额外说明。");
        return parseJsonRef(raw.content(), typeRef);
    }

    /**
     * 协议级结构化输出(推荐)
     * <p>用 Spring AI BeanOutputConverter 把 Java 类型转成 JSON Schema,
     * 让 LLM 在协议层就约束输出格式(走 OpenAI response_format=json_object)。
     * <p>与 chatJson 的区别:
     * <ul>
     *   <li>chatJson: 靠 prompt 提示 + 手 parse → 脆弱,LLM 不听话就崩</li>
     *   <li>chatStructured: JSON schema 注入 + 自动 entity() → 协议层保证</li>
     * </ul>
     *
     * @param type 目标 Java 类型(record / class 均可)
     * @return 解析后的对象;LLM 不可用或解析失败时返回 null(让调用方决定兜底)
     */
    public <T> T chatStructured(String systemPrompt, String userPrompt, Class<T> type) {
        long t0 = System.currentTimeMillis();
        try {
            T result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(type);
            log.debug("LLM chatStructured ok: {}ms, type={}", System.currentTimeMillis() - t0, type.getSimpleName());
            return result;
        } catch (Exception e) {
            log.warn("LLM chatStructured failed (LLM unavailable or bad schema): {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T parseJson(String content, Class<T> type) {
        try {
            String json = stripCodeFence(content);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("JSON 解析失败: {}", e.getMessage());
            return (T) mockJson(type);
        }
    }

    private <T> T parseJsonRef(String content, TypeReference<T> typeRef) {
        try {
            String json = stripCodeFence(content);
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String stripCodeFence(String content) {
        if (content == null) return "";
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        return json;
    }

    /** 文本嵌入(zvec 写入与查询都用) */
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("embedding failed: {}", e.getMessage());
            // 离线兜底:全 0 向量(zvec 不可用时上层会切到内存实现)
            return new float[properties.zvec().dim()];
        }
    }

    /* -------------------- 内部 -------------------- */

    private String mockAnswer(String system, String user) {
        // LLM 不可用时的兜底文本:给上层 Agent 看,不要直接透传给用户
        // (各 Agent 应在 fallback() 里写自己的用户友好文案)
        return "MOCK";
    }

    private <T> T mockJson(Class<T> type) {
        try {
            return objectMapper.readValue("{}", type);
        } catch (Exception e) {
            throw new IllegalStateException("mock fallback failed", e);
        }
    }

    private static String abbreviate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private static Map<String, Object> extractUsage(ChatResponse resp) {
        if (resp == null || resp.getMetadata() == null) return null;
        var usage = resp.getMetadata().getUsage();
        if (usage == null) return null;
        return Map.of(
                "promptTokens", usage.getPromptTokens(),
                "completionTokens", usage.getCompletionTokens(),
                "totalTokens", usage.getTotalTokens()
        );
    }

    /** 聊天结果(供 Agent / Orchestrator 记录调用链) */
    public record LlmResult(String content, boolean success, long durationMs, Map<String, Object> usage) {}
}
