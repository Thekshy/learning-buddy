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
 * 聊天 Controller —— 用户主入口(带持久化记忆)
 *
 * <p>POST /api/chat          发送消息(经 Orchestrator Function Calling 派发;消息落库,历史注入)
 * <p>GET  /api/agent-calls?request_id=xxx  拉取 Agent 调用链
 *
 * <p>记忆:每次请求都先存 user 消息 → 注入该会话最近 N 条历史 → 调 LLM → 存 assistant 回复。
 * <p>双保险:后端 DB 历史为主,前端传的 history 为补充。
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

    public record ChatRequest(String message, Boolean useRag, List<String> history, Long sessionId) {}

    @PostMapping("/chat")
    @Transactional
    public ResponseEntity<?> chat(@RequestBody ChatRequest req, Authentication auth) {
        Long userId = userIdOf(auth);

        // 1. 取或建会话
        var session = sessionService.getOrCreateSession(userId, req.sessionId());
        Long sessionId = session.getId();

        // 2. 存 user 消息(落库,成为下次的记忆)
        sessionService.saveMessage(sessionId, "USER", req.message(), null);

        // 3. 构造上下文,注入 DB 历史(主)+ 前端历史(辅)
        AgentContext ctx = AgentContext.create(userId, sessionId, req.message());
        if (req.useRag() != null) ctx.putSlot("useRag", req.useRag());
        if (req.history() != null) ctx.putSlot("history", req.history());

        // DB 历史作为主记忆源(不含当前轮,更可靠)
        String dbHistory = sessionService.recentHistoryAsText(sessionId);
        if (dbHistory != null) {
            ctx.putData("dbHistory", dbHistory);
        }

        // 4. 设置 UserContext(供 Tool 方法内部读 userId,无需 LLM 传参)
        userContext.set(userId);
        try {
            // 5. 调 Orchestrator
            var resp = orchestrator.dispatch(ctx);

            // 6. 存 assistant 回复
            sessionService.saveMessage(sessionId, "ASSISTANT", resp.reply(),
                    resp.toolNames().isEmpty() ? "TUTOR" : resp.toolNames().get(0));

            // 7. 取出本次请求 generateQuiz 暂存的题目(前端答题用)
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
