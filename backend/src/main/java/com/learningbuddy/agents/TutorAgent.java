package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import com.learningbuddy.rag.Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 答疑 Agent
 *
 * <p>支持 RAG 检索增强:从 zvec 拉 TopK 片段,拼到 system prompt。
 * <p>支持上下文记忆:会话历史由调用方传入 messages,放在 user message 之前。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TutorAgent implements BaseAgent {

    private final LlmClient llm;
    private final Retriever retriever;

    @Override
    public String name() {
        return "Tutor";
    }

    @Override
    public AgentResult handle(AgentContext ctx) {
        String userQuestion = (String) ctx.getSlot("rawInput");
        Boolean useRag = (Boolean) ctx.getSlotOrDefault("useRag", false);
        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) ctx.getSlotOrDefault("history", List.of());

        String context = "";
        if (Boolean.TRUE.equals(useRag)) {
            var hits = retriever.retrieve(userQuestion, 5);
            context = hits.stream()
                    .map(h -> "[%d] %s".formatted(h.chunkId(), h.snippet()))
                    .reduce("", (a, b) -> a + "\n" + b);
            ctx.putData("rag_hits", hits);
        }

        String system = """
                你是耐心、循循善诱的 AI 老师,讲解清晰、会举例、会追问。
                %s
                请用中文回答。如引用了资料,用 [1][2] 这样的标号标记。""".formatted(
                context.isBlank() ? "" : "以下是相关参考资料:\n" + context);

        StringBuilder user = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            user.append("对话历史:\n");
            for (int i = 0; i < history.size(); i++) {
                user.append(i % 2 == 0 ? "用户: " : "助教: ").append(history.get(i)).append("\n");
            }
            user.append("\n");
        }
        user.append("当前问题: ").append(userQuestion);

        try {
            var llmResult = llm.chat(system, user.toString());
            String answer = llmResult.success() ? llmResult.content() : fallbackAnswer(userQuestion, context);
            return AgentResult.builder()
                    .success(true)  // 即便走了 fallback,也算成功(用户拿到了能用的回答)
                    .reply(answer)
                    .payload(Map.of(
                            "answer", answer,
                            "ragUsed", !context.isBlank(),
                            "ragHits", ctx.getData("rag_hits")
                    ))
                    .meta(Map.of(
                            "durationMs", llmResult.durationMs(),
                            "llmSuccess", llmResult.success()
                    ))
                    .build();
        } catch (Exception e) {
            log.error("Tutor failed", e);
            // 用 HashMap 而不是 Map.of:异常信息可能为 null,Map.of 不允许 null 值
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("fallback", true);
            if (e.getMessage() != null) meta.put("error", e.getMessage());
            return AgentResult.builder()
                    .success(true)  // fallback 也是成功路径
                    .reply(fallbackAnswer(userQuestion, context))
                    .meta(meta)
                    .build();
        }
    }

    /** 离线兜底:基于问题关键词给个像样的回答,不让用户看到 [MOCK] */
    private String fallbackAnswer(String question, String context) {
        if (!context.isBlank()) {
            return "根据现有资料:\n\n" + context.split("\n")[0]
                    + "\n\n(LLM 暂不可用,以上为资料原文摘录)";
        }
        if (question == null || question.isBlank()) return "请问一下你想了解什么?";
        return "我理解你想了解:「" + question + "」\n\n"
                + "建议先用 1-2 句话描述你目前的理解 / 卡点,我会针对性讲解。"
                + "(当前 LLM 暂不可用,这是离线兜底回复)";
    }
}
