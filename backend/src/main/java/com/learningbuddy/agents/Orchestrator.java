package com.learningbuddy.agents;

import com.learningbuddy.graph.AgentCallRecorder;
import com.learningbuddy.graph.CallContext;
import com.learningbuddy.tools.LearningTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Orchestrator — Spring AI Function Calling + 官方 ChatMemory。
 *
 * <p>记忆不再手动拼 system prompt:由 MessageChatMemoryAdvisor 自动把历史作为
 * 真实多轮 Message 注入。Orchestrator 只负责:角色 prompt + 动态会话 id + 工具调度。
 *
 * <p>工具(LearningTools)和记忆 advisor 已在 ChatClientConfig 注册为默认,
 * 这里只需 .advisors(spec → param(CONVERSATION_ID, sessionId)) 指定当前会话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final ChatClient chatClient;
    private final AgentCallRecorder recorder;
    private final CallContext callContext;
    private final LearningTools tools;

    public OrchestratorResponse dispatch(AgentContext ctx) {
        String systemPrompt = buildSystemPrompt(ctx);
        String userMessage = (String) ctx.getSlot("rawInput");
        Long sessionId = ctx.getSessionId();
        String sessionIdStr = sessionId != null ? sessionId.toString() : null;

        // 记录 Orchestrator 根节点(调用链的起点,工具节点挂在它下面)
        Long rootCallId = recorder.startSimple(
                ctx.getRequestId(), "Orchestrator", "dispatch",
                abbreviate(userMessage, 100));

        // 设置 CallContext:把 requestId + parentCallId 传给 ToolCallingManager,让它能记录工具调用
        callContext.setRequestId(ctx.getRequestId());
        callContext.setParentCallId(rootCallId);
        try {
            // 1. 调 ChatClient
            //    - 记忆由 MessageChatMemoryAdvisor 自动注入(需传 conversationId = sessionId)
            //    - 工具每次显式传入(不在 ChatClientConfig 注册为 defaultTools,避免循环依赖)
            var promptSpec = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(tools);
            if (sessionIdStr != null) {
                promptSpec = promptSpec.advisors(spec ->
                        spec.param(ChatMemory.CONVERSATION_ID, sessionIdStr));
            }
            ChatResponse response = promptSpec.call().chatResponse();

            AssistantMessage assistantMessage = response.getResult().getOutput();
            String finalText = assistantMessage.getText();

            // 2. 工具调用名从 AgentCallRecorder 取(它由 ToolCallingManager 记录了真实调用)
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
            log.warn("Orchestrator LLM call failed, returning offline fallback: {}", e.getMessage());
            return new OrchestratorResponse(
                    ctx.getRequestId(),
                    List.of(),
                    "⚠️ LLM 服务暂不可用（当前为离线演示模式）。\n\n"
                    + "你的需求已收到：「" + abbreviate(userMessage, 80) + "」\n"
                    + "配置真实的 MiniMax API Key 后即可使用完整的多智能体功能（路径规划 / 出题 / 答疑 / 资源推荐 / 复盘）。",
                    Map.of("agentResults", List.of(), "tools", List.of(), "toolCount", 0)
            );
        } finally {
            callContext.clear();
        }
    }

    /* ===================== 内部 ===================== */

    private String buildSystemPrompt(AgentContext ctx) {
        StringBuilder sb = new StringBuilder("""
                你是「智能学习伙伴」的协调 Agent,负责理解用户学习需求,选择合适的工具来帮助用户。
                你有 5 个工具可用:
                  1. planLearningPath  - 制定学习路径(用户想学某知识点时)
                  2. generateQuiz       - 生成练习题(用户要题 / 想被考时)
                  3. answerQuestion     - 答疑解惑(用户问具体概念 / 怎么 / 为什么;若用户上传过资料,会自动 RAG)
                  4. recommendResources - 推荐资源(用户要教程 / 资料)
                  5. reviewProgress     - 复盘进度(从 DB 读真实做题统计,用户做完了 / 要总结)

                使用规则:
                  - 可以一次调用多个工具(如果用户的需求涉及多个方面)
                  - 调用工具时,从用户输入中合理推断参数
                  - 调用工具后,根据工具返回的结果,用友好、清晰、鼓励性的语言回复用户
                  - 中文回答,语气像朋友 + 老师
                  - 你会自动获得之前的对话历史(多轮记忆),请结合上下文理解用户当前输入
                """);

        // 阶段4钩子:掌握度画像注入(实现后由 LearnerProfileService 提供)
        String profile = ctx.getData("learnerProfile");
        if (profile != null && !profile.isBlank()) {
            sb.append("\n\n").append(profile);
        }

        // 阶段3钩子:向量检索长期记忆(实现后由 SemanticMemoryService 提供)
        String relatedMemory = ctx.getData("relatedMemory");
        if (relatedMemory != null && !relatedMemory.isBlank()) {
            sb.append("\n\n").append(relatedMemory);
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
