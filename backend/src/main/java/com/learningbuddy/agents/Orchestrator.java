package com.learningbuddy.agents;

import com.learningbuddy.graph.AgentCallRecorder;
import com.learningbuddy.graph.CallContext;
import com.learningbuddy.tools.LearningTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
    private final LearningTools tools;
    private final CallContext callContext;

    public OrchestratorResponse dispatch(AgentContext ctx) {
        String systemPrompt = buildSystemPrompt(ctx);
        String userMessage = (String) ctx.getSlot("rawInput");

        // 记录 Orchestrator 根节点(调用链的起点,工具节点挂在它下面)
        Long rootCallId = recorder.startSimple(
                ctx.getRequestId(), "Orchestrator", "dispatch",
                abbreviate(userMessage, 100));

        // 设置 CallContext:把 requestId + parentCallId 传给 ToolCallingManager,让它能记录工具调用
        callContext.setRequestId(ctx.getRequestId());
        callContext.setParentCallId(rootCallId);
        try {
            // 1. 调 ChatClient,.tools(tools) 把 @Tool bean 显式注册给 LLM
            //    Spring AI 内部完成 function calling 循环,LoggingToolCallingManager 会记录每次工具调用
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(tools)
                    .call()
                    .chatResponse();

            AssistantMessage assistantMessage = response.getResult().getOutput();
            String finalText = assistantMessage.getText();

            // 2. 工具调用名从 AgentCallRecorder 取(它由 ToolCallingManager 记录了真实调用)
            //    最终 assistant message 经完整 tool 循环后已不含 toolCalls,所以不能从它取
            List<String> toolNames = recorder.getByRequest(ctx.getRequestId()).stream()
                    .filter(r -> !"Orchestrator".equals(r.agentName))
                    .map(r -> r.agentName)
                    .distinct()
                    .toList();
            log.info("LLM response: tools={}, text.len={}", toolNames.size(), finalText == null ? 0 : finalText.length());

            // 3. 关闭 Orchestrator 根节点(标记整体成功)
            recorder.finish(rootCallId, "SUCCESS",
                    "分发 " + toolNames.size() + " 个工具: " + toolNames, null);

            return new OrchestratorResponse(
                    ctx.getRequestId(),
                    toolNames,
                    finalText,
                    Map.of(
                            "agentResults", recorder.getByRequest(ctx.getRequestId()),
                            "tools", toolNames,
                            "toolCount", toolNames.size()
                    )
            );
        } catch (RuntimeException e) {
            recorder.finish(rootCallId, "FAILED", null, abbreviate(e.getMessage(), 300));
            throw e;
        } finally {
            callContext.clear();
        }
    }

    /* ===================== 内部 ===================== */

    /**
     * 工具调用记录现已由 LoggingToolCallingManager 自动接管(捕获入参/返回值/耗时),
     * Orchestrator 不再手动记录 —— buildSystemPrompt 之后直接返回。
     */

    private String buildSystemPrompt(AgentContext ctx) {
        StringBuilder sb = new StringBuilder("""
                你是「智能学习伙伴」的协调 Agent,负责理解用户学习需求,选择合适的工具来帮助用户。
                你有 5 个工具可用:
                  1. planLearningPath  - 制定学习路径(用户想学某知识点时)
                  2. generateQuiz       - 生成练习题(用户要题 / 想被考时)
                  3. answerQuestion     - 答疑解惑(用户问具体概念 / 怎么 / 为什么;若用户上传过资料,会自动 RAG)
                  4. recommendResources - 推荐资源(用户要教程 / 资料)
                  5. reviewProgress     - 复盘进度(用户做完了 / 要总结)

                使用规则:
                  - 可以一次调用多个工具(如果用户的需求涉及多个方面)
                  - 调用工具时,从用户输入中合理推断参数
                  - 调用工具后,根据工具返回的结果,用友好、清晰、鼓励性的语言回复用户
                  - 中文回答,语气像朋友 + 老师
                """);

        // 注入聊天历史(让 LLM 有多轮上下文)
        // 主记忆源:后端 DB 历史(SessionService 写入,刷新/换设备不丢)
        String dbHistory = ctx.getData("dbHistory");
        if (dbHistory != null && !dbHistory.isBlank()) {
            sb.append("\n\n以下是之前的对话记录,请结合上下文理解本轮用户输入:\n").append(dbHistory);
        } else {
            // 辅助记忆源:前端传的 history(首次加载或 DB 为空时兜底)
            @SuppressWarnings("unchecked")
            List<String> history = (List<String>) ctx.getSlot("history");
            if (history != null && !history.isEmpty()) {
                sb.append("\n\n以下是之前的对话记录,请结合上下文理解本轮用户输入:\n");
                int n = Math.min(history.size(), 10);   // 只回放最近 10 轮,控制 token
                for (int i = history.size() - n; i < history.size(); i++) {
                    sb.append("- ").append(history.get(i)).append("\n");
                }
            }
        }
        return sb.toString();
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
