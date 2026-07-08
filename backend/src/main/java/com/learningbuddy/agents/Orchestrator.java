package com.learningbuddy.agents;

import com.learningbuddy.core.LlmClient;
import com.learningbuddy.graph.AgentCallRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator(协调 Agent)——系统的"大脑"
 *
 * <p>职责:
 * <ol>
 *   <li>意图分类(LLM 解析用户输入属于哪类请求)</li>
 *   <li>槽位抽取(从输入里抽 subject / node / level 等)</li>
 *   <li>调度:根据意图,串/并行调用专职 Agent</li>
 *   <li>汇总:把所有 Agent 的 reply / payload 拼成最终响应</li>
 *   <li>调用链日志:每次 Agent 调用都通过 {@link AgentCallRecorder} 记录</li>
 * </ol>
 *
 * <p>调度策略(简化版,可后续升级为 DAG):
 * <ul>
 *   <li>LEARN    → Planner → (并行) Quiz + Recommender</li>
 *   <li>QUIZ     → Quiz</li>
 *   <li>ANSWER   → Tutor(可选 RAG)</li>
 *   <li>RECOMMEND→ Recommender</li>
 *   <li>REVIEW   → Reviewer</li>
 *   <li>GREETING → 不调 Agent,直接回寒暄</li>
 *   <li>UNKNOWN  → Tutor 兜底</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final LlmClient llm;
    private final AgentCallRecorder recorder;
    private final PlannerAgent planner;
    private final QuizAgent quiz;
    private final TutorAgent tutor;
    private final RecommenderAgent recommender;
    private final ReviewerAgent reviewer;

    public OrchestratorResponse dispatch(AgentContext ctx) {
        // 1. 意图分类
        Long orchestratorCallId = recorder.start(ctx, "Orchestrator", "dispatch", "开始处理");
        AgentContext.Intent intent;
        try {
            intent = classifyIntent(ctx);
        } catch (Exception e) {
            log.warn("intent classification failed, fallback TUTOR: {}", e.getMessage());
            intent = AgentContext.Intent.ANSWER;
        }
        ctx.setIntent(intent);
        // 2. 槽位抽取(粗粒度:正则/关键词,避免多一次 LLM 调用)
        extractSlots(ctx);
        recorder.finish(orchestratorCallId, "SUCCESS", "intent=" + intent, null);

        // 3. 调度
        List<AgentResult> results = new ArrayList<>();
        switch (intent) {
            case LEARN -> {
                runAgent(ctx, planner, results);
                runAgent(ctx, quiz, results);
                runAgent(ctx, recommender, results);
            }
            case QUIZ -> runAgent(ctx, quiz, results);
            case ANSWER -> runAgent(ctx, tutor, results);
            case RECOMMEND -> runAgent(ctx, recommender, results);
            case REVIEW -> runAgent(ctx, reviewer, results);
            case GREETING -> results.add(greeting());
            default -> runAgent(ctx, tutor, results);
        }

        // 4. 汇总
        String combinedReply = results.stream()
                .filter(AgentResult::isSuccess)
                .map(AgentResult::getReply)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("抱歉,没有可用的回答。");
        return new OrchestratorResponse(ctx.getRequestId(), intent, combinedReply,
                Map.of("agentResults", results, "slots", ctx.getSlots()));
    }

    /* -------------------- 内部 -------------------- */

    private void runAgent(AgentContext ctx, BaseAgent agent, List<AgentResult> sink) {
        Long callId = recorder.start(ctx, agent.name(), "handle", "ctx=" + ctx.getRequestId());
        try {
            AgentResult r = agent.handle(ctx);
            recorder.finish(callId, r.isSuccess() ? "SUCCESS" : "FAILED",
                    abbreviate(r.getReply()), r.getError());
            sink.add(r);
        } catch (Exception e) {
            log.error("{} threw: {}", agent.name(), e.getMessage(), e);
            recorder.finish(callId, "FAILED", null, e.getMessage());
            sink.add(AgentResult.builder()
                    .success(false)
                    .reply(agent.name() + " 处理失败")
                    .error(e.getMessage())
                    .build());
        }
    }

    private AgentContext.Intent classifyIntent(AgentContext ctx) {
        String system = """
                你是意图分类器。根据用户输入,只输出一个词(枚举):
                  LEARN(想学某知识点) / QUIZ(要题) / ANSWER(答疑) /
                  RECOMMEND(要资源) / REVIEW(看进度) / UPLOAD(传资料) /
                  GREETING(寒暄) / UNKNOWN
                严格只输出枚举名。""";
        String user = (String) ctx.getSlot("rawInput");
        var r = llm.chat(system, user);
        String word = r.content().trim().split("\\s+")[0].toUpperCase();
        return switch (word) {
            case "LEARN"     -> AgentContext.Intent.LEARN;
            case "QUIZ"      -> AgentContext.Intent.QUIZ;
            case "ANSWER"    -> AgentContext.Intent.ANSWER;
            case "RECOMMEND" -> AgentContext.Intent.RECOMMEND;
            case "REVIEW"    -> AgentContext.Intent.REVIEW;
            case "UPLOAD"    -> AgentContext.Intent.UPLOAD;
            case "GREETING"  -> AgentContext.Intent.GREETING;
            default          -> AgentContext.Intent.UNKNOWN;
        };
    }

    /** 极简槽位抽取 —— 演示阶段不依赖 NLU,后续可换 LLM 抽取或换 NLPCraft */
    private void extractSlots(AgentContext ctx) {
        String text = (String) ctx.getSlot("rawInput");
        if (text == null) return;
        String lower = text.toLowerCase();

        // 学科(常见关键词)
        if (lower.contains("python") || lower.contains("装饰器") || lower.contains("函数")) {
            ctx.putSlot("subject", "python");
        } else if (lower.contains("高数") || lower.contains("微积分") || lower.contains("极限")) {
            ctx.putSlot("subject", "math");
        } else if (lower.contains("英语") || lower.contains("cet") || lower.contains("词汇")) {
            ctx.putSlot("subject", "english");
        } else {
            ctx.putSlot("subject", "python");  // 默认
        }

        // 水平
        if (lower.contains("初学") || lower.contains("新手") || lower.contains("入门") || lower.contains("不会")) {
            ctx.putSlot("level", "BEGINNER");
        } else if (lower.contains("进阶") || lower.contains("中级") || lower.contains("熟练")) {
            ctx.putSlot("level", "INTERMEDIATE");
        } else if (lower.contains("高级") || lower.contains("精通") || lower.contains("老手")) {
            ctx.putSlot("level", "ADVANCED");
        } else {
            ctx.putSlot("level", "BEGINNER");
        }

        // 知识点(粗略,取关键词)
        if (lower.contains("装饰器")) ctx.putSlot("node", "装饰器");
        else if (lower.contains("函数")) ctx.putSlot("node", "函数");
        else if (lower.contains("面向对象") || lower.contains("oop") || lower.contains("类")) ctx.putSlot("node", "面向对象");
        else if (lower.contains("极限")) ctx.putSlot("node", "极限");
        else if (lower.contains("导数")) ctx.putSlot("node", "导数");
        else ctx.putSlot("node", "综合基础");

        // 题目数(QUIZ 时用)
        ctx.putSlot("quizCount", 3);
        // RAG(ANSWER 时看用户有没有要)
        ctx.putSlot("useRag", lower.contains("资料") || lower.contains("课件") || lower.contains("笔记"));
    }

    private AgentResult greeting() {
        return AgentResult.builder()
                .success(true)
                .reply("你好!我是你的智能学习伙伴。我可以帮你:\n" +
                        "1. 制定学习计划(例如:「我想学 Python 装饰器,我是初学者」)\n" +
                        "2. 出练习题(「给我来 5 道题」)\n" +
                        "3. 答疑解惑(直接提问)\n" +
                        "4. 推荐学习资源(「有什么好资源」)\n" +
                        "5. 复盘学习进度(「看看我学得怎么样」)")
                .build();
    }

    private static String abbreviate(String s) {
        if (s == null) return null;
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    /** Orchestrator 顶层响应 */
    public record OrchestratorResponse(
            String requestId,
            AgentContext.Intent intent,
            String reply,
            Map<String, Object> detail
    ) {}
}
