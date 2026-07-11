package com.learningbuddy.core;

import com.learningbuddy.services.QuizService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本次请求内 generateQuiz 产出的暂存区(ThreadLocal)
 *
 * <p>背景:Orchestrator 通过 Spring AI 调用 @Tool,工具返回值被框架内部消费,
 * ChatController 默认拿不到。但我们要把 quizId + questions 传给前端答题。
 *
 * <p>方案:generateQuiz 落库后把结果存到这里,ChatController 在 dispatch 返回后取走,
 * 放入 HTTP 响应。请求结束 {@link #clear()}。
 *
 * <p>注:阶段 4 的 LoggingToolCallingManager 会用更通用的方式捕获所有工具返回值,
 * 到时这个 holder 可移除;现在用它是因为改动最小且立即生效。
 */
@Component
public class QuizResultHolder {

    private static final ThreadLocal<List<QuizService.SavedQuiz>> CURRENT = ThreadLocal.withInitial(ArrayList::new);

    public void add(QuizService.SavedQuiz saved) {
        CURRENT.get().add(saved);
    }

    public List<QuizService.SavedQuiz> drain() {
        List<QuizService.SavedQuiz> list = CURRENT.get();
        CURRENT.remove();
        return list;
    }

    public void clear() {
        CURRENT.remove();
    }
}
