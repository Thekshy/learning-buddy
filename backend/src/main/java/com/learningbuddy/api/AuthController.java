package com.learningbuddy.api;

import com.learningbuddy.models.AppUser;
import com.learningbuddy.repositories.AppUserRepository;
import com.learningbuddy.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * 鉴权 Controller(JPA 持久化版)
 * <p>注册/登录读写 DB,重启不丢用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppUserRepository userRepository;

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            String displayName
    ) {}
    public record LoginReq(@NotBlank String username, @NotBlank String password) {}
    public record AuthResp(String token, Long userId, String username, String displayName) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        if (userRepository.existsByUsername(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "username exists"));
        }
        AppUser user = AppUser.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .displayName(req.displayName() != null ? req.displayName() : req.username())
                .role("STUDENT")
                .build();
        user = userRepository.save(user);
        String token = jwtService.issue(user.getId(), user.getUsername());
        return ResponseEntity.ok(new AuthResp(token, user.getId(), user.getUsername(), user.getDisplayName()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        AppUser user = userRepository.findByUsername(req.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }
        String token = jwtService.issue(user.getId(), user.getUsername());
        return ResponseEntity.ok(new AuthResp(token, user.getId(), user.getUsername(), user.getDisplayName()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        return ResponseEntity.ok(Map.of("user", auth.getPrincipal()));
    }
}
