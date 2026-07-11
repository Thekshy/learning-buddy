package com.learningbuddy.api;

import com.learningbuddy.security.JwtAuthFilter;
import com.learningbuddy.services.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Quiz 闭环 Controller
 *
 * <p>POST /api/quiz/{quizId}/grade  提交答案判分 → 写 attempt + 错题本 + 更新掌握度
 */
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    public record GradeRequest(List<AnswerDto> answers) {}
    public record AnswerDto(Long questionId, String userAnswer, Integer elapsedMs) {}

    @PostMapping("/{quizId}/grade")
    public ResponseEntity<?> grade(@PathVariable Long quizId,
                                   @RequestBody GradeRequest req,
                                   Authentication auth) {
        Long userId = userIdOf(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        List<QuizService.Answer> answers = req.answers().stream()
                .map(a -> new QuizService.Answer(a.questionId(), a.userAnswer(), a.elapsedMs()))
                .toList();
        var result = quizService.grade(userId, quizId, answers);
        return ResponseEntity.ok(result);
    }

    private static Long userIdOf(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtAuthFilter.AuthenticatedUser u)) {
            return null;
        }
        return u.id();
    }
}
