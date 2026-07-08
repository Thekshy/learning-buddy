package com.learningbuddy.agents;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent 调用的统一返回(供 Orchestrator 汇总 + 调用链日志使用)
 */
@Data
@Builder
public class AgentResult {

    /** 是否成功 */
    private boolean success;

    /** 用户可见的简短回复(给聊天 UI) */
    private String reply;

    /** 结构化数据(给前端组件渲染,如 plan/quiz/resources) */
    private Object payload;

    /** 元信息:耗时、token、RAG 命中数等 */
    @Builder.Default
    private Map<String, Object> meta = Map.of();

    /** 失败原因(如有) */
    private String error;
}
