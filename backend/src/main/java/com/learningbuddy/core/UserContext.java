package com.learningbuddy.core;

import org.springframework.stereotype.Component;

/**
 * 当前请求的用户上下文(ThreadLocal holder)
 *
 * <p>问题:LearningTools 的 @Tool 方法签名由 LLM 填参数,userId 不该暴露给 LLM;
 * 但 generateQuiz 落库需要 userId。
 *
 * <p>解决:ChatController 在调 Orchestrator 前 {@link #set(Long)},
 * Tool 方法内部 {@link #get()} 读取;请求结束 {@link #clear()}。
 *
 * <p>生命周期:Orchestrator.dispatch 是同步调用,在同一线程内完成,
 * ThreadLocal 在请求线程内可见,clear 后无泄漏。
 */
@Component
public class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    public void set(Long userId) {
        CURRENT.set(userId);
    }

    public Long get() {
        return CURRENT.get();
    }

    public void clear() {
        CURRENT.remove();
    }
}
