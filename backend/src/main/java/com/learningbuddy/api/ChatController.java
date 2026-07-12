package com.learningbuddy.api;

import com.learningbuddy.agents.AgentContext;
import com.learningbuddy.agents.Orchestrator;
import com.learningbuddy.core.QuizResultHolder;
import com.learningbuddy.core.UserContext;
import com.learningbuddy.graph.AgentCallRecorder;
import com.learningbuddy.security.JwtAuthFilter;
import com.learningbuddy.services.QuizService;
import com.learningbuddy.services.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 Controller —— 用户主入口(官方 ChatMemory 记忆)
 *
 * <p>POST /api/chat          发送消息(经 Orchestrator Function Calling 派发)
 * <p>GET  /api/agent-calls?request_id=xxx  拉取 Agent 调用链
 *
 * <p>记忆由 MessageChatMemoryAdvisor 自动管理:
 * advisor 在请求前注入该会话历史(真实多轮 Message),请求后把新消息写回 DB。
 * Controller 只需确保会话存在 + 传 sessionId 给 Orchestrator。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final Orchestrator orchestrator;
    private final AgentCallRecorder recorder;
    private final SessionService sessionService;
    private final UserContext userContext;
    private final QuizResultHolder quizHolder;
    private final com.learningbuddy.services.ConversationSummaryService summaryService;
    private final com.learningbuddy.services.SemanticMemoryService semanticMemoryService;
    private final com.learningbuddy.services.LearnerProfileService learnerProfileService;
    private final com.learningbuddy.services.SessionTitleService titleService;

    public record ChatRequest(String message, Boolean useRag, List<String> history, Long sessionId) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req, Authentication auth) {
        Long userId = userIdOf(auth);

        // 1. 取或建会话(advisor 用 sessionId 作 conversationId 读写记忆)
        var session = sessionService.getOrCreateSession(userId, req.sessionId());
        Long sessionId = session.getId();

        // 2. 构造上下文(记忆由 advisor 自动注入,无需手动拼历史)
        AgentContext ctx = AgentContext.create(userId, sessionId, req.message());
        if (req.useRag() != null) ctx.putSlot("useRag", req.useRag());

        // 语义长期记忆:跨会话检索与当前输入相关的历史片段
        try {
            String related = semanticMemoryService.retrieveRelated(userId, req.message());
            if (related != null) {
                ctx.putData("relatedMemory", related);
            }
        } catch (Exception e) {
            // 语义检索失败不影响主流程
        }

        // 学习者画像:注入掌握度,让 LLM 知道用户哪里薄弱
        try {
            String profile = learnerProfileService.buildProfile(userId);
            if (profile != null) {
                ctx.putData("learnerProfile", profile);
            }
        } catch (Exception e) {
            // 画像构建失败不影响主流程
        }

        // 3. 设置 UserContext(供 Tool 方法内部读 userId,无需 LLM 传参)
        userContext.set(userId);
        try {
            // 4. 调 Orchestrator(advisor 自动:注入历史 → 调 LLM → 写回新消息)
            var resp = orchestrator.dispatch(ctx);

            // 5. 取出本次请求 generateQuiz 暂存的题目(前端答题用)
            List<QuizService.SavedQuiz> quizzes = quizHolder.drain();
            List<Map<String, Object>> quizPayload = quizzes.stream()
                    .map(q -> (Map<String, Object>) toQuizDto(q))
                    .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requestId", resp.requestId());
            body.put("sessionId", sessionId);
            body.put("tools", resp.toolNames());
            body.put("reply", resp.reply());
            body.put("detail", resp.detail());
            if (!quizPayload.isEmpty()) {
                body.put("quiz", quizPayload.get(0));   // 单次通常一道 quiz;取第一个
            }
            return ResponseEntity.ok(body);
        } finally {
            userContext.clear();
            quizHolder.clear();
            // 异步:摘要压缩 + 标题生成(都不阻塞响应)
            summaryService.summarizeIfNeeded(sessionId);
            titleService.generateTitleIfNeeded(sessionId);
        }
    }

    @GetMapping("/agent-calls")
    public ResponseEntity<?> calls(@RequestParam("request_id") String requestId) {
        return ResponseEntity.ok(Map.of("calls", recorder.getByRequest(requestId)));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.AuthenticatedUser u)) {
            return null;
        }
        return u.id();
    }

    /** SavedQuiz → 前端 quiz DTO(quizId + 带 questionId 的题目列表) */
    private static Map<String, Object> toQuizDto(QuizService.SavedQuiz saved) {
        List<Map<String, Object>> questions = saved.questions().stream()
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("questionId", q.questionId());
                    m.put("type", q.type());
                    m.put("stem", q.stem());
                    m.put("options", q.options());
                    m.put("analysis", q.analysis());
                    // 不暴露 answer 给前端(防作弊)
                    return m;
                })
                .toList();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("quizId", saved.quizId());
        dto.put("questions", questions);
        return dto;
    }
}
