package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 资源推荐 Agent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommenderAgent implements BaseAgent {

    private final LlmClient llm;

    @Override
    public String name() {
        return "Recommender";
    }

    @Override
    public AgentResult handle(AgentContext ctx) {
        String subject = (String) ctx.getSlot("subject");
        String node    = (String) ctx.getSlot("node");

        String system = """
                你是学习资源策划。根据目标知识点,推荐 5 个学习资源(DOC 文档 / VIDEO 视频 / TUTORIAL 教程 / PROJECT 项目)。
                字段:title, type, url(可真实可占位), description, difficulty(1-5)。
                严格 JSON 数组输出,不要额外文本。""";
        String user = "学科:" + subject + ",知识点:" + node;

        try {
            List<Resource> recs = llm.chatJson(system, user,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Resource>>() {});
            ctx.putData("resources", recs);
            return AgentResult.builder()
                    .success(true)
                    .reply("已推荐 " + recs.size() + " 个学习资源。")
                    .payload(recs)
                    .meta(Map.of("count", recs.size()))
                    .build();
        } catch (Exception e) {
            log.error("Recommender failed: {}", e.getMessage());
            return AgentResult.builder()
                    .success(true)
                    .reply("[MOCK 离线] 已展示示例资源")
                    .payload(fallback(node))
                    .meta(Map.of("fallback", true))
                    .build();
        }
    }

    private List<Resource> fallback(String node) {
        String title = node == null ? "目标知识点" : node;
        return List.of(
                new Resource("官方文档: " + title, "DOC", "https://docs.python.org/3/", "权威参考", 2),
                new Resource("入门视频教程", "VIDEO", "https://www.bilibili.com/video/BV1xxx", "B 站热门教程", 1),
                new Resource("动手项目: 写个小工具", "PROJECT", "https://github.com/topics/python", "巩固所学", 3)
        );
    }

    public record Resource(String title, String type, String url, String description, int difficulty) {}
}
