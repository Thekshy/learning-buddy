package com.learningbuddy.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learningbuddy.core.LlmClient;
import com.learningbuddy.core.QuizResultHolder;
import com.learningbuddy.core.UserContext;
import com.learningbuddy.rag.Retriever;
import com.learningbuddy.services.QuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 学习工具集 — 5 个 @Tool 注解方法
 *
 * <p>每个 Tool 由 LLM 自行决定是否调用(基于 @Tool.description)。
 * <p>Tool 是无状态的:接参数,返回结果。Orchestrator 负责记录 + 编排。
 *
 * <p>这种"Function Calling"模式的优点:
 * <ul>
 *   <li>LLM 决定调哪个 / 调几个,不需要硬编码路由</li>
 *   <li>加新 Tool = 加 @Tool 方法,不动 Orchestrator</li>
 *   <li>复合请求(「教我 X 然后考考我」)自然处理(LLM 一次调多个 Tool)</li>
 *   <li>LLM 负责抽取 Tool 参数,代码更简洁</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningTools {

    private final LlmClient llm;
    private final Retriever retriever;
    private final QuizService quizService;
    private final UserContext userContext;
    private final QuizResultHolder quizHolder;

    /** zvec TopK,从 application.yml 的 learningbuddy.zvec.topk 注入 */
    @Value("${learningbuddy.zvec.topk:5}")
    private int ragTopK;

    /**
     * 1. 学习路径规划
     */
    @Tool(description = """
            为用户规划学习路径。根据目标知识点和当前水平,设计 2-4 个阶段的学习计划。
            每阶段包含:phase 序号、title、goals 目标列表、durationHours 预估时长、outputs 产出物。
            在以下情况调用:
              - 用户说"想学 X"、"教我 X"、"学习 X"等
              - 用户提供了目标知识点和水平
            """)
    public PlanResult planLearningPath(
            @ToolParam(description = "学科,如 python / math / english") String subject,
            @ToolParam(description = "目标知识点,如 装饰器、函数、极限") String node,
            @ToolParam(description = "用户当前水平:BEGINNER / INTERMEDIATE / ADVANCED") String level) {
        log.info("Tool.planLearningPath: subject={}, node={}, level={}", subject, node, level);
        String system = """
                你是资深学习路径规划师。根据用户的目标知识点和当前水平,设计 2-4 阶段的学习路径。
                每阶段需包含:phase(序号)、title、goals(目标列表)、durationHours、outputs。
                严格 JSON 输出,不要任何额外文本。""";
        String user = String.format("学科:%s,知识点:%s,水平:%s", subject, node, level);

        try {
            PlanResult plan = llm.chatJson(system, user, PlanResult.class);
            if (plan == null || plan.phases() == null || plan.phases().isEmpty()) {
                return fallbackPlan(node);
            }
            return plan;
        } catch (Exception e) {
            log.warn("planLearningPath failed: {}", e.getMessage());
            return fallbackPlan(node);
        }
    }

    /**
     * 2. 出练习题(落库,返回 quizId 供前端提交答案)
     */
    @Tool(description = """
            根据知识点和难度生成 N 道练习题,题型以 CHOICE 选择题为主,可含 FILL 填空题。
            返回结果含 quizId 和每题 questionId —— 回复用户时请附上 quizId,用户答题后前端用它提交判分。
            在以下情况调用:
              - 用户说"出题"、"考考我"、"给我来几道题"等
              - 教学过程中需要检验用户掌握度
            """)
    public QuizDto generateQuiz(
            @ToolParam(description = "学科,如 python / math / english") String subject,
            @ToolParam(description = "知识点,如 装饰器、函数") String node,
            @ToolParam(description = "题目数量,默认 3 道,范围 1-10", required = false) Integer count) {
        log.info("Tool.generateQuiz: subject={}, node={}, count={}", subject, node, count);
        if (count == null || count <= 0) count = 3;
        count = Math.min(count, 10);

        String system = String.format("""
                你是出题老师。根据知识点生成 %d 道练习题,题型 CHOICE 选择题为主(可含 FILL)。
                字段: type(CHOICE/FILL), stem 题干, options 4 选(CHOICE 时), answer 正确答案, analysis 解析。
                严格 JSON 数组输出。""", count);
        String user = String.format("学科:%s,知识点:%s", subject, node);

        List<Question> qs;
        try {
            qs = llm.chatJson(system, user, new TypeReference<List<Question>>() {});
            if (qs == null || qs.isEmpty()) qs = fallbackQuestions(count);
        } catch (Exception e) {
            log.warn("generateQuiz failed, use fallback: {}", e.getMessage());
            qs = fallbackQuestions(count);
        }
        qs = qs.subList(0, Math.min(count, qs.size()));

        // 落库,生成 quizId + questionId(前端答题提交用)
        Long userId = userContext.get();
        if (userId != null) {
            try {
                QuizService.SavedQuiz saved = quizService.saveGeneratedQuiz(userId, subject, node, qs);
                log.info("Quiz persisted: quizId={}", saved.quizId());
                quizHolder.add(saved);   // 暂存,ChatController 取出传给前端
                return QuizDto.fromSaved(saved);
            } catch (Exception e) {
                log.warn("Quiz persist failed, return without id: {}", e.getMessage());
            }
        }
        // userId 取不到或落库失败:返回不带 id 的(前端无法判分,但不影响展示)
        return QuizDto.unsaved(qs);
    }

    /**
     * 3. 答疑解惑
     */
    @Tool(description = """
            对用户的问题进行详细解答,要求讲解清晰、会举例、能追问。
            支持 RAG 检索增强(如果用户上传了参考资料)。
            在以下情况调用:
              - 用户提问具体的知识点、概念、错误
              - 用户问"为什么"、"怎么"、"是什么"、"区别"
            """)
    public TutorResult answerQuestion(
            @ToolParam(description = "用户的具体问题") String question,
            @ToolParam(description = "可选,知识点上下文,帮 LLM 更好地回答", required = false) String context) {
        log.info("Tool.answerQuestion: question={}, context={}", question, context);

        // RAG 检索:从已上传资料中找 TopK 命中片段
        List<Retriever.Hit> hits;
        try {
            hits = retriever.retrieve(question, ragTopK);
        } catch (Exception e) {
            log.warn("RAG retrieve failed, will answer without context: {}", e.getMessage());
            hits = List.of();
        }
        boolean ragUsed = hits != null && !hits.isEmpty();

        StringBuilder system = new StringBuilder("""
                你是耐心、循循善诱的 AI 老师,讲解清晰、会举例、会追问。
                请用中文回答,字数 200-400 字。""");
        if (ragUsed) {
            system.append("\n\n以下是从用户上传资料中检索到的相关片段,回答时请参考并可在文末标注来源序号 [n]:");
            for (int i = 0; i < hits.size(); i++) {
                Retriever.Hit h = hits.get(i);
                system.append(String.format("\n[%d] (来源:%s) %s", i + 1, h.docName(), h.snippet()));
            }
        }

        String user = context != null && !context.isBlank()
                ? String.format("知识点上下文:%s\n问题:%s", context, question)
                : question;

        try {
            var r = llm.chat(system.toString(), user);
            if (r.success()) {
                return new TutorResult(r.content(), ragUsed);
            }
        } catch (Exception e) {
            log.warn("answerQuestion failed: {}", e.getMessage());
        }
        // 兜底
        return new TutorResult(
                "我理解你想了解:「" + question + "」\n\n建议先用 1-2 句话描述你目前的理解 / 卡点,我会针对性讲解。",
                ragUsed);
    }

    /**
     * 4. 推荐学习资源
     */
    @Tool(description = """
            根据学习主题推荐 3-5 个学习资源(文档 / 视频 / 教程 / 项目)。
            在以下情况调用:
              - 用户说"推荐资源"、"有什么好教程"、"学什么"等
              - 教学过程中需要补充材料
            """)
    public List<Resource> recommendResources(
            @ToolParam(description = "学习主题或知识点") String topic) {
        log.info("Tool.recommendResources: topic={}", topic);
        String system = """
                你是学习资源策划。根据主题推荐 3-5 个学习资源。
                字段: title, type (DOC/VIDEO/TUTORIAL/PROJECT), url, description, difficulty (1-5)。
                严格 JSON 数组输出。""";
        String user = "主题:" + topic;

        try {
            List<Resource> recs = llm.chatJson(system, user, new TypeReference<List<Resource>>() {});
            if (recs != null && !recs.isEmpty()) return recs;
        } catch (Exception e) {
            log.warn("recommendResources failed: {}", e.getMessage());
        }
        return fallbackResources(topic);
    }

    /**
     * 5. 复盘学习进度(从 DB 读真实做题数据,不再要用户口报)
     */
    @Tool(description = """
            生成学习复盘反馈。会自动读取用户真实的做题记录(总题数/正确数/错题数),无需用户口报。
            返回:summary 总结、strengths 优点、weaknesses 不足、nextSteps 下一步建议。
            在以下情况调用:
              - 用户说"复盘"、"总结"、"看看我学得怎么样"、"我学得如何"等
              - 用户刚做完题想看反馈
            """)
    public ReviewResult reviewProgress(
            @ToolParam(description = "可选,额外说明用户想复盘的方向,如'装饰器'、'最近'", required = false) String focus) {
        log.info("Tool.reviewProgress: focus={}", focus);

        // 从 DB 读真实统计
        Long userId = userContext.get();
        long total = 0, correct = 0, wrongCount = 0;
        if (userId != null) {
            try {
                QuizService.UserStats stats = quizService.userStats(userId);
                total = stats.total();
                correct = stats.correct();
                wrongCount = stats.wrongCount();
            } catch (Exception e) {
                log.warn("read user stats failed: {}", e.getMessage());
            }
        }
        double accuracy = total == 0 ? 0 : (correct * 100.0 / total);
        log.info("reviewProgress real stats: total={}, correct={}, wrong={}", total, correct, wrongCount);

        if (total == 0) {
            // 用户还没做过题
            return new ReviewResult(
                    "还没有做题记录。建议先让我出几道题练练手!",
                    List.of("迈出第一步就是最好的开始"),
                    List.of("暂无数据,先做几题再说"),
                    List.of("试试说:「考考我 Python 函数」")
            );
        }

        String system = """
                你是学习复盘教练。根据用户真实的做题数据生成简短、有针对性的反馈。
                严格 JSON:{"summary":"...","strengths":["..."],"weaknesses":["..."],"nextSteps":["..."]}""";
        String user = String.format("做题数据 → 总题数:%d,正确:%d,错误:%d,正确率:%.0f%%。复盘方向:%s",
                total, correct, wrongCount, accuracy, focus != null ? focus : "整体");

        try {
            ReviewResult result = llm.chatJson(system, user, ReviewResult.class);
            if (result != null) return result;
        } catch (Exception e) {
            log.warn("reviewProgress LLM failed: {}", e.getMessage());
        }
        return new ReviewResult(
                String.format("已完成 %d 题,正确 %d 题(正确率 %.0f%%)", total, correct, accuracy),
                correct > 0 ? List.of("部分题目已掌握") : List.of("继续努力"),
                wrongCount > 0 ? List.of("有 " + wrongCount + " 道错题待巩固") : List.of("保持节奏"),
                List.of("建议复做错题,再做 5 道同类型练习")
        );
    }

    /* ===================== 兜底 ===================== */

    private PlanResult fallbackPlan(String node) {
        String title = "学习" + (node == null ? "目标知识点" : node);
        return new PlanResult(title, List.of(
                new Phase(1, "基础概念", List.of("理解定义", "能口述"), 2, List.of("笔记")),
                new Phase(2, "动手实践", List.of("完成 3 个小练习"), 3, List.of("代码片段")),
                new Phase(3, "综合应用", List.of("完成综合项目"), 4, List.of("项目报告"))
        ));
    }

    private List<Question> fallbackQuestions(int count) {
        List<Question> qs = List.of(
                new Question("CHOICE", "Python 中用于定义函数的关键字是?",
                        List.of("function", "def", "fun", "define"),
                        "def", "Python 用 def 定义函数,如 def foo(): pass"),
                new Question("CHOICE", "下列哪个是 Python 的内置装饰器?",
                        List.of("@override", "@staticmethod", "@deprecated", "@inline"),
                        "@staticmethod", "@staticmethod 是 Python 内置装饰器,把方法变为静态方法"),
                new Question("FILL", "执行 print('Hi'.__len__()) 的结果是? (填数字)",
                        null, "2", "字符串 'Hi' 长度为 2,__len__() 返回长度")
        );
        return qs.subList(0, Math.min(count, qs.size()));
    }

    private List<Resource> fallbackResources(String topic) {
        String title = topic == null ? "目标知识点" : topic;
        return List.of(
                new Resource("官方文档: " + title, "DOC", "https://docs.python.org/3/", "权威参考", 2),
                new Resource("入门视频教程", "VIDEO", "https://www.bilibili.com/", "B 站热门教程", 1),
                new Resource("动手项目: 写个小工具", "PROJECT", "https://github.com/topics/python", "巩固所学", 3)
        );
    }

    /* ===================== DTO ===================== */

    public record PlanResult(String title, List<Phase> phases) {}
    public record Phase(int phase, String title, List<String> goals, int durationHours, List<String> outputs) {}

    /** generateQuiz 返回给 LLM 的结构(含 quizId,前端答题提交用) */
    public record QuizDto(Long quizId, List<QuestionDto> questions, String quizType) {
        /** 从落库结果构造 */
        public static QuizDto fromSaved(QuizService.SavedQuiz saved) {
            List<QuestionDto> qs = saved.questions().stream()
                    .map(sq -> new QuestionDto(sq.questionId(), sq.type(), sq.stem(), sq.options(), sq.answer(), sq.analysis()))
                    .toList();
            return new QuizDto(saved.quizId(), qs, "PRACTICE");
        }

        /** 未落库(无 quizId,前端只展示不能判分) */
        public static QuizDto unsaved(List<Question> raw) {
            List<QuestionDto> qs = raw.stream()
                    .map(q -> new QuestionDto(null, q.type(), q.stem(), q.options(), q.answer(), q.analysis()))
                    .toList();
            return new QuizDto(null, qs, "PRACTICE");
        }
    }

    /** 落库后的题目(带 questionId) */
    public record QuestionDto(Long questionId, String type, String stem, List<String> options, String answer, String analysis) {}

    /** LLM 生成的原始题目(无 id) */
    public record Question(String type, String stem, List<String> options, String answer, String analysis) {}

    public record TutorResult(String answer, boolean ragUsed) {}

    public record Resource(String title, String type, String url, String description, int difficulty) {}

    public record ReviewResult(String summary, List<String> strengths, List<String> weaknesses, List<String> nextSteps) {}
}
