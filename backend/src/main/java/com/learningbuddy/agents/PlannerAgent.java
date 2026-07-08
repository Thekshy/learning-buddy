package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 路径规划 Agent
 *
 * <p>输入:目标知识点 + 当前水平
 * <p>输出:分阶段学习计划(每阶段:知识点、目标、预估耗时、产出物)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent implements BaseAgent {

    private final LlmClient llm;

    @Override
    public String name() {
        return "Planner";
    }

    @Override
    public AgentResult handle(AgentContext ctx) {
        String subject = (String) ctx.getSlot("subject");
        String node    = (String) ctx.getSlot("node");
        String level   = (String) ctx.getSlot("level");

        String system = """
                你是资深学习路径规划师。根据用户的目标知识点和当前水平,设计一个 2-4 阶段的学习路径。
                每阶段需包含:phase(序号)、title、goals(目标列表)、durationHours(预估学习时长)、outputs(产出物,如笔记/练习)。
                输出必须是严格 JSON,不要任何额外文本。
                """;
        String user = String.format("""
                {
                  "subject": "%s",
                  "targetNode": "%s",
                  "level": "%s",
                  "rawInput": "%s"
                }
                """, subject, node, level, ctx.getSlot("rawInput"));

        try {
            PlanPayload plan = llm.chatJson(system, user, PlanPayload.class);
            ctx.putData("plan", plan);
            return AgentResult.builder()
                    .success(true)
                    .reply("已为你生成 " + (plan.phases() == null ? 0 : plan.phases().size())
                            + " 个阶段的学习路径。")
                    .payload(plan)
                    .meta(Map.of("phaseCount", plan.phases() == null ? 0 : plan.phases().size()))
                    .build();
        } catch (Exception e) {
            log.error("Planner failed: {}", e.getMessage());
            return fallback(node);
        }
    }

    /** 离线兜底:固定 3 阶段模板,保证演示链不中断 */
    private AgentResult fallback(String node) {
        PlanPayload plan = new PlanPayload(
                "学习" + (node == null ? "目标知识点" : node),
                List.of(
                        new Phase(1, "基础概念", List.of("理解定义", "能口述"), 2, List.of("笔记")),
                        new Phase(2, "动手实践", List.of("完成 3 个小练习"), 3, List.of("代码片段")),
                        new Phase(3, "综合应用", List.of("完成综合项目"), 4, List.of("项目报告"))
                )
        );
        return AgentResult.builder()
                .success(true)
                .reply("[MOCK 离线] 已生成示例学习路径")
                .payload(plan)
                .meta(Map.of("fallback", true))
                .build();
    }

    /* ----- DTO(供 Jackson 反序列化) ----- */
    public record PlanPayload(String title, List<Phase> phases) {}
    public record Phase(int phase, String title, List<String> goals, int durationHours, List<String> outputs) {}
}
