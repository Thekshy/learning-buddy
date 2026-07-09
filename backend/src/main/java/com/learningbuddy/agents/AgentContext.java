package com.learningbuddy.agents;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 一次请求的 Agent 上下文(在 Orchestrator 与各 Agent 之间流转)
 *
 * <p>每个字段代表"到目前为止我们已经知道了什么":
 * <ul>
 *   <li>{@code requestId}  一次用户请求的唯一 ID,贯穿所有 Agent,用于调用链日志关联</li>
 *   <li>{@code userId}     当前登录用户</li>
 *   <li>{@code sessionId}  聊天会话</li>
 *   <li>{@code intent}     Orchestrator 分类出的意图</li>
 *   <li>{@code slots}      从用户输入抽取的关键信息(目标知识点 / 水平 / 学科等)</li>
 *   <li>{@code data}       各 Agent 写入的产出(plan / quiz / resources / progress)</li>
 *   <li>{@code parentCallId} 父调用 ID(用于构建调用树)</li>
 * </ul>
 */
@Data
@Builder
public class AgentContext {

    private String requestId;
    private Long userId;
    private Long sessionId;
    private Intent intent;

    /** 抽取的槽位,如 {"subject":"python","node":"装饰器","level":"BEGINNER"} */
    @Builder.Default
    private Map<String, Object> slots = new HashMap<>();

    /** 各 Agent 的产出:plan / quiz / resources / progress / rag_hits 等 */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /** 调用链:parentCallId 用于构建 Agent 树 */
    private Long parentCallId;

    public static AgentContext create(Long userId, Long sessionId, String rawInput) {
        Map<String, Object> slots = new HashMap<>();
        slots.put("rawInput", rawInput);
        return AgentContext.builder()
                .requestId(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(sessionId)
                .slots(slots)
                .data(new HashMap<>())
                .build();
    }

    public void putData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    public void putSlot(String key, Object value) {
        slots.put(key, value);
    }

    public <T> T getSlot(String key) {
        @SuppressWarnings("unchecked")
        T v = (T) slots.get(key);
        return v;
    }

    public Object getSlotOrDefault(String key, Object defaultValue) {
        return slots.getOrDefault(key, defaultValue);
    }

    /** 用户意图分类 */
    public enum Intent {
        LEARN,        // "我想学 X"
        QUIZ,         // "给我来几道题"
        ANSWER,       // "这个怎么理解"
        RECOMMEND,    // "有什么资源"
        REVIEW,       // "看看我学得怎么样"
        UPLOAD,       // 上传资料
        GREETING,     // 寒暄
        UNKNOWN
    }
}
