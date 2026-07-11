package com.learningbuddy.api;

import com.learningbuddy.models.ChatMessage;
import com.learningbuddy.models.ChatSession;
import com.learningbuddy.security.JwtAuthFilter;
import com.learningbuddy.services.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理 Controller
 *
 * <p>POST   /api/sessions                创建会话
 * <p>GET    /api/sessions                列出当前用户会话
 * <p>GET    /api/sessions/{id}/messages  拉取会话历史消息
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) CreateSessionReq req, Authentication auth) {
        Long userId = userIdOf(auth);
        ChatSession s = sessionService.createSession(userId, req != null ? req.title() : null);
        return ResponseEntity.ok(toDto(s));
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        Long userId = userIdOf(auth);
        List<Map<String, Object>> list = sessionService.listSessions(userId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(Map.of("sessions", list));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<?> messages(@PathVariable Long id, Authentication auth) {
        List<ChatMessage> msgs = sessionService.listMessages(id);
        List<Map<String, Object>> list = msgs.stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "agentKind", m.getAgentKind() != null ? m.getAgentKind() : "",
                        "createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                ))
                .toList();
        return ResponseEntity.ok(Map.of("messages", list));
    }

    private Map<String, Object> toDto(ChatSession s) {
        return Map.of(
                "id", s.getId(),
                "title", s.getTitle(),
                "agentKind", s.getAgentKind() != null ? s.getAgentKind() : "",
                "createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "",
                "updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : ""
        );
    }

    public record CreateSessionReq(String title) {}

    private static Long userIdOf(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.AuthenticatedUser u)) {
            return null;
        }
        return u.id();
    }
}
