package com.learningbuddy.graph;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import java.util.List;

/**
 * 调用链增强:用日志版 ToolCallingManager 覆盖默认,捕获每个工具的入参/返回值/耗时。
 *
 * <p>方案:声明一个 ToolCallingManager @Bean(@ConditionalOnMissingBean 顶掉框架默认),
 * 内部委托给 DefaultToolCallingManager,只在 executeToolCalls 前后插桩。
 *
 * <p>效果:前端调用链卡片从"调了 planLearningPath"升级为
 * "调了 planLearningPath,耗时 320ms,产出:[...]"。
 *
 * <p>requestId 关联:executeToolCalls 由 Spring AI 在 chatClient.call() 内部触发,
 * 此时仍在请求线程上,可从 {@link CallContext#currentRequestId()} 取到
 * ChatController 设置的 requestId,据此写入 AgentCallRecorder。
 */
@Slf4j
@Configuration
public class LoggingToolCallingManager {

    @Bean
    @ConditionalOnMissingBean
    ToolCallingManager toolCallingManager(
            ToolCallbackResolver resolver,
            ToolExecutionExceptionProcessor exProcessor,
            ObjectProvider<ObservationRegistry> observationRegistry,
            AgentCallRecorder recorder) {

        // 把 recorder 注入 CallContext,供非 bean 的 LoggingDelegate 使用
        CallContext.recorder = recorder;

        // 委托目标:和框架默认装配完全一致
        DefaultToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(resolver)
                .toolExecutionExceptionProcessor(exProcessor)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();

        return new LoggingDelegate(delegate);
    }

    /** 真正干活的包装器:委托 + 计时 + 记录 */
    static class LoggingDelegate implements ToolCallingManager {
        private final ToolCallingManager delegate;

        LoggingDelegate(ToolCallingManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<ToolDefinition> resolveToolDefinitions(
                org.springframework.ai.model.tool.ToolCallingChatOptions options) {
            return delegate.resolveToolDefinitions(options);
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            // 委托前:从 assistant message 取本轮要调的工具(名 + 入参)
            List<ToolCall> calls = chatResponse.getResult().getOutput().getToolCalls();
            long t0 = System.currentTimeMillis();

            ToolExecutionResult result;
            try {
                result = delegate.executeToolCalls(prompt, chatResponse);
            } catch (RuntimeException e) {
                long ms = System.currentTimeMillis() - t0;
                // 记录失败
                String requestId = CallContext.currentRequestId();
                if (requestId != null && CallContext.recorder != null) {
                    for (ToolCall c : calls) {
                        recordFail(requestId, c, ms, e.toString());
                    }
                }
                throw e;
            }

            // 委托后:从 conversationHistory 末尾的 ToolResponseMessage 取每个工具返回值
            long ms = System.currentTimeMillis() - t0;
            List<ToolResponseMessage.ToolResponse> responses = extractResponses(result);
            log.info("[TOOL-BATCH] {} tools, took={}ms, responses={}",
                    calls.size(), ms, responses.size());

            String requestId = CallContext.currentRequestId();
            if (requestId != null && CallContext.recorder != null) {
                for (int i = 0; i < calls.size(); i++) {
                    String ret = (i < responses.size()) ? responses.get(i).responseData() : null;
                    recordSuccess(requestId, calls.get(i), ms, ret);
                }
            }
            return result;
        }

        private void recordSuccess(String requestId, ToolCall call, long ms, String returnValue) {
            Long callId = CallContext.recorder.startSimple(
                    requestId, call.name(), "tool_call",
                    abbreviate(call.arguments(), 200));
            String output = returnValue != null ? abbreviate(returnValue, 500) : "(no return value)";
            CallContext.recorder.finish(callId, "SUCCESS",
                    output + " (耗时 " + ms + "ms)", null);
        }

        private void recordFail(String requestId, ToolCall call, long ms, String err) {
            Long callId = CallContext.recorder.startSimple(
                    requestId, call.name(), "tool_call",
                    abbreviate(call.arguments(), 200));
            CallContext.recorder.finish(callId, "FAILED", null, abbreviate(err, 300));
        }

        /** 从 ToolExecutionResult 的 conversationHistory 末尾找 ToolResponseMessage */
        private static List<ToolResponseMessage.ToolResponse> extractResponses(ToolExecutionResult result) {
            if (result == null || result.conversationHistory() == null) return List.of();
            List<org.springframework.ai.chat.messages.Message> history = result.conversationHistory();
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i) instanceof ToolResponseMessage trm) {
                    return trm.getResponses();
                }
            }
            return List.of();
        }

        private static String abbreviate(String s, int n) {
            if (s == null) return null;
            return s.length() <= n ? s : s.substring(0, n) + "...";
        }
    }
}
