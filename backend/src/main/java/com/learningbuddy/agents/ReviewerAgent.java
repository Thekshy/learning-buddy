package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 复盘 Agent
 *
 * <p>职责:判卷 + 错题归集 + 掌握度更新 + 给 Planner 调整建议
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewerAgent implements BaseAgent {

    private final LlmClient llm;

    @Override
    public String name() {
        return "Reviewer";
    }

    @Override
    public AgentResult handle(AgentContext ctx) {
        // 简化版:不做完整判分,演示阶段把"做对的题数 / 总题数"算出来
        // 等 D4 接上 JPA 时,这里会读 attempt 表,写 progress / wrong_book
        Integer total = (Integer) ctx.getSlotOrDefault("totalQuestions", 0);
        Integer correct = (Integer) ctx.getSlotOrDefault("correctCount", 0);
        double accuracy = total == 0 ? 0.0 : (correct * 1.0 / total);

        String system = """
                你是学习复盘教练。基于用户的做题表现,生成简短复盘反馈 + 后续学习建议。
                严格 JSON:{"summary":"...","strengths":["..."],"weaknesses":["..."],"nextSteps":["..."]}""";
        String user = String.format("""
                总题数:%d,正确:%d,正确率:%.0f%%
                """, total, correct, accuracy * 100);

        try {
            var payload = llm.chatJson(system, user, java.util.Map.class);
            ctx.putData("review", payload);
            return AgentResult.builder()
                    .success(true)
                    .reply("已生成复盘报告,正确率 " + (int) (accuracy * 100) + "%")
                    .payload(payload)
                    .meta(Map.of("accuracy", accuracy))
                    .build();
        } catch (Exception e) {
            log.error("Reviewer failed: {}", e.getMessage());
            return AgentResult.builder()
                    .success(true)
                    .reply("[MOCK 离线] 复盘反馈:正确率 " + (int) (accuracy * 100) + "%")
                    .payload(Map.of(
                            "summary", "完成 " + total + " 题,正确 " + correct + " 题",
                            "strengths", List.of("基础概念掌握尚可"),
                            "weaknesses", List.of("细节应用需加强"),
                            "nextSteps", List.of("建议复做错题,再做 5 道同类型练习")
                    ))
                    .meta(Map.of("fallback", true, "accuracy", accuracy))
                    .build();
        }
    }
}
