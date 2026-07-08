package com.learningbuddy.api;

import com.learningbuddy.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鉴权 Controller(简化版)
 * <p>开发期用内存存储用户,演示用;生产应接 JPA + MySQL/PG。
 * <p>不依赖数据库,便于离线演示,符合"用到再创建实体类"的原则。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /** 简易内存用户表(userId -> [username, passwordHash]) */
    private final Map<String, String[]> users = new ConcurrentHashMap<>();
    private final Map<String, Long> userIds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1);

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            String displayName
    ) {}
    public record LoginReq(@NotBlank String username, @NotBlank String password) {}
    public record AuthResp(String token, Long userId, String username) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (users.containsKey(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "username exists"));
        }
        long id = seq.getAndIncrement();
        users.put(req.username(), new String[]{passwordEncoder.encode(req.password()), req.displayName()});
        userIds.put(req.username(), id);
        String token = jwtService.issue(id, req.username());
        return ResponseEntity.ok(new AuthResp(token, id, req.username()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        String[] stored = users.get(req.username());
        if (stored == null || !passwordEncoder.matches(req.password(), stored[0])) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }
        long id = userIds.get(req.username());
        String token = jwtService.issue(id, req.username());
        return ResponseEntity.ok(new AuthResp(token, id, req.username()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        return ResponseEntity.ok(Map.of("user", auth.getPrincipal()));
    }
}
