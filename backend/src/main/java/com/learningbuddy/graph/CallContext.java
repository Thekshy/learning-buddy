package com.learningbuddy.graph;

import org.springframework.stereotype.Component;

/**
 * 调用链上下文(ThreadLocal)—— 把 requestId 传给 ToolCallingManager。
 *
 * <p>背景:ToolCallingManager.executeToolCalls 由 Spring AI 在 chatClient.call() 内部触发,
 * 拿不到 AgentContext。但它在同一线程执行,故用 ThreadLocal 传递当前 requestId,
 * 据此把工具调用的入参/返回值/耗时写入 AgentCallRecorder。
 *
 * <p>生命周期:Orchestrator.dispatch 前 set,返回后 clear(同 UserContext)。
 * <p>{@code recorder} 是静态字段:LoggingToolCallingManager 在启动时注入一次,
 * 供其内部的 LoggingDelegate(非 Spring bean)访问。
 */
@Component
public class CallContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    /** 由 LoggingToolCallingManager 装配时注入,供非 bean 的 LoggingDelegate 使用 */
    static AgentCallRecorder recorder;

    /** 由 Orchestrator 设置 */
    public void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public void clear() {
        REQUEST_ID.remove();
    }

    /** 供 LoggingDelegate 读取 */
    static String currentRequestId() {
        return REQUEST_ID.get();
    }
}
