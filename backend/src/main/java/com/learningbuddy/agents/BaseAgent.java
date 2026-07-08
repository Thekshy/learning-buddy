package com.learningbuddy.agents;

/**
 * 所有专职 Agent 的统一接口
 *
 * <p>实现要点:
 * <ol>
 *   <li>{@link #name()}  是 Agent 唯一标识,会写到 agent_call_log.agent_name</li>
 *   <li>{@link #handle(AgentContext)} 在子类里应捕获异常,失败时返回 success=false
 *       + error 描述,而不是抛出(让 Orchestrator 能继续调度后续 Agent)</li>
 *   <li>任何 LLM 调用都通过 {@link com.learningbuddy.core.LlmClient},不要直接拿 ChatClient</li>
 * </ol>
 */
public interface BaseAgent {

    String name();

    AgentResult handle(AgentContext ctx);
}
