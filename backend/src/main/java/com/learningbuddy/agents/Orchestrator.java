package com.learningbuddy.agents;

import com.learningbuddy.graph.AgentCallRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrator — Spring AI Function Calling 模式
 *
 * <p>不再用固定 Intent 枚举路由:把 5 个 Tool(plan/quiz/tutor/recommend/review)
 * 注册给 LLM,LLM 自己决定调哪个 / 调几个,Spring AI 内部完成 function calling 循环。
 *
 * <p>优势:
 * <ul>
 *   <li>加新 Tool = 加 @Tool 方法,不动 Orchestrator</li>
 *   <li>LLM 自由组合 Tool(「教我 X 然后考考我」自然处理)</li>
 *   <li>每次 Tool 调用被 AgentCallRecorder 记录(可视化用)</li>
 *   <li>LLM 自动抽取 Tool 参数,代码无需手动解析</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final ChatClient chatClient;
    private final AgentCallRecorder recorder;

    public OrchestratorResponse dispatch(AgentContext ctx) {
        String systemPrompt = buildSystemPrompt();
        String userMessage = (String) ctx.getSlot("rawInput");

        // 1. 调 ChatClient,tools() 自动注册 @Tool bean
        //    Spring AI 内部完成 function calling 循环,我们只拿到最终响应
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .chatResponse();

        AssistantMessage assistantMessage = response.getResult().getOutput();

        // 2. 记录 LLM 调了哪些 Tool(供可视化)
        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        List<AgentResult> agentResults = recordToolCalls(ctx, toolCalls);

        String finalText = assistantMessage.getText();
        log.info("LLM response: tools={}, text.len={}", toolCalls.size(), finalText == null ? 0 : finalText.length());

        return new OrchestratorResponse(
                ctx.getRequestId(),
                toolCalls.stream().map(ToolCall::name).toList(),
                finalText,
                Map.of(
                        "agentResults", agentResults,
                        "tools", toolCalls.stream().map(ToolCall::name).toList(),
                        "toolCount", toolCalls.size()
                )
        );
    }

    /* ===================== 内部 ===================== */

    private List<AgentResult> recordToolCalls(AgentContext ctx, List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("LLM 直接回答,未调用 Tool");
            return List.of();
        }
        log.info("LLM 调了 {} 个 Tool: {}",
                toolCalls.size(),
                toolCalls.stream().map(ToolCall::name).collect(Collectors.toList()));

        // 注:Spring AI 的 chatClient.tools() 已经把 Tool 执行完了,
        // 这里只能"后置"记录调用(没有 tool 实际返回值的访问)。
        // 记录"调了哪个 / 调了几个"已经够可视化用了。
        List<AgentResult> results = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            Long callId = recorder.start(ctx, call.name(), "tool_call",
                    abbreviate(call.arguments() == null ? "{}" : call.arguments().toString(), 200));
            recorder.finish(callId, "SUCCESS", "LLM 选用了 " + call.name() + " (Spring AI 已自动执行)", null);
            results.add(AgentResult.builder()
                    .success(true)
                    .reply("已调用 " + call.name())
                    .payload(Map.of("toolName", call.name(),
                            "arguments", String.valueOf(call.arguments())))
                    .meta(Map.of("toolName", call.name()))
                    .build());
        }
        return results;
    }

    private String buildSystemPrompt() {
        return """
                你是「智能学习伙伴」的协调 Agent,负责理解用户学习需求,选择合适的工具来帮助用户。
                你有 5 个工具可用:
                  1. planLearningPath  - 制定学习路径(用户想学某知识点时)
                  2. generateQuiz       - 生成练习题(用户要题 / 想被考时)
                  3. answerQuestion     - 答疑解惑(用户问具体概念 / 怎么 / 为什么)
                  4. recommendResources - 推荐资源(用户要教程 / 资料)
                  5. reviewProgress     - 复盘进度(用户做完了 / 要总结)

                使用规则:
                  - 可以一次调用多个工具(如果用户的需求涉及多个方面)
                  - 调用工具时,从用户输入中合理推断参数
                  - 调用工具后,根据工具返回的结果,用友好、清晰、鼓励性的语言回复用户
                  - 中文回答,语气像朋友 + 老师
                """;
    }

    private static String abbreviate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /* ===================== 响应 DTO ===================== */

    public record OrchestratorResponse(
            String requestId,
            List<String> toolNames,
            String reply,
            Map<String, Object> detail
    ) {}
}
