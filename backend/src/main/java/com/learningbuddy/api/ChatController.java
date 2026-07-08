package com.learningbuddy.api;

import com.learningbuddy.agents.AgentContext;
import com.learningbuddy.agents.Orchestrator;
import com.learningbuddy.graph.AgentCallRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天 Controller —— 用户主入口
 *
 * <p>POST /api/chat        发送消息(经 Orchestrator 派发)
 * <p>GET  /api/agent-calls?request_id=xxx  拉取 Agent 调用链
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final Orchestrator orchestrator;
    private final AgentCallRecorder recorder;

    public record ChatRequest(String message, Boolean useRag, List<String> history) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req, Authentication auth) {
        Long userId = userIdOf(auth);
        AgentContext ctx = AgentContext.create(userId, null, req.message());
        if (req.useRag() != null) ctx.putSlot("useRag", req.useRag());
        if (req.history() != null) ctx.putSlot("history", req.history());
        var resp = orchestrator.dispatch(ctx);
        return ResponseEntity.ok(Map.of(
                "requestId", resp.requestId(),
                "intent", resp.intent(),
                "reply", resp.reply(),
                "detail", resp.detail()
        ));
    }

    @GetMapping("/agent-calls")
    public ResponseEntity<?> calls(@RequestParam("request_id") String requestId) {
        return ResponseEntity.ok(Map.of("calls", recorder.getByRequest(requestId)));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof com.learningbuddy.security.JwtAuthFilter.AuthenticatedUser u)) {
            return null;
        }
        return u.id();
    }
}
