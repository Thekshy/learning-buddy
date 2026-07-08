package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 练习题 Agent
 *
 * <p>支持题型:CHOICE 选择 / FILL 填空 / SHORT 简答 / CODE 编程
 * <p>出题后自动判卷 + 给出解析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuizAgent implements BaseAgent {

    private final LlmClient llm;

    @Override
    public String name() {
        return "Quiz";
    }

    @Override
    public AgentResult handle(AgentContext ctx) {
        String subject = (String) ctx.getSlot("subject");
        String node    = (String) ctx.getSlot("node");
        Integer count  = (Integer) ctx.getSlotOrDefault("quizCount", 3);

        String system = """
                你是出题老师。根据知识点生成 %d 道练习题,题型以 CHOICE 为主(可含 FILL)。
                严格 JSON 数组输出,字段:
                  - type(CHOICE/FILL)
                  - stem(题干)
                  - options(CHOICE 时为 4 个选项的数组,否则 null)
                  - answer(正确答案:CHOICE 为选项内容,FILL 为字符串)
                  - analysis(解析)
                不要任何额外文本。
                """.formatted(count);

        String user = String.format("学科:%s,知识点:%s", subject, node);

        try {
            List<Question> questions = llm.chatJson(system, user,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Question>>() {});
            ctx.putData("questions", questions);
            return AgentResult.builder()
                    .success(true)
                    .reply("已生成 " + questions.size() + " 道练习题。")
                    .payload(Map.of("questions", questions, "quizType", "PRACTICE"))
                    .meta(Map.of("count", questions.size()))
                    .build();
        } catch (Exception e) {
            log.error("Quiz failed: {}", e.getMessage());
            return fallback(subject, node, count);
        }
    }

    /** 离线兜底:经典 hello world 类选择题,演示用 */
    private AgentResult fallback(String subject, String node, int count) {
        List<Question> qs = List.of(
                new Question("CHOICE",
                        "Python 中用于定义函数的关键字是?",
                        List.of("function", "def", "fun", "define"),
                        "def",
                        "Python 用 def 定义函数,如 def foo(): pass"),
                new Question("CHOICE",
                        "下列哪个是 Python 的内置装饰器?",
                        List.of("@override", "@staticmethod", "@deprecated", "@inline"),
                        "@staticmethod",
                        "@staticmethod 是 Python 内置装饰器,把方法变为静态方法"),
                new Question("FILL",
                        "执行 print('Hi'.__len__()) 的结果是? (填数字)",
                        null,
                        "2",
                        "字符串 'Hi' 长度为 2,__len__() 返回长度")
        );
        return AgentResult.builder()
                .success(true)
                .reply("[MOCK 离线] 已生成示例题目")
                .payload(Map.of("questions", qs.subList(0, Math.min(count, qs.size())), "quizType", "PRACTICE"))
                .meta(Map.of("fallback", true, "count", Math.min(count, qs.size())))
                .build();
    }

    /** DTO */
    public record Question(String type, String stem, List<String> options, String answer, String analysis) {}
}
