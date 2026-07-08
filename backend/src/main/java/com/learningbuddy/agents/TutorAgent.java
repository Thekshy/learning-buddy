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
            return AgentResult.builder()
                    .success(true)
                    .reply(llmResult.content())
                    .payload(Map.of(
                            "answer", llmResult.content(),
                            "ragUsed", !context.isBlank(),
                            "ragHits", ctx.getData("rag_hits")
                    ))
                    .meta(Map.of(
                            "durationMs", llmResult.durationMs(),
                            "llmSuccess", llmResult.success()
                    ))
                    .build();
        } catch (Exception e) {
            log.error("Tutor failed: {}", e.getMessage());
            return AgentResult.builder()
                    .success(false)
                    .reply("答疑暂时不可用,请稍后再试。")
                    .error(e.getMessage())
                    .build();
        }
    }
}
