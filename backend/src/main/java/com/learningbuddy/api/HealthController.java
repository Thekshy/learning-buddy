package com.learningbuddy.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查 + 服务自描述
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "learning-buddy",
                "ts", Instant.now().toString(),
                "version", "0.1.0"
        );
    }

    @GetMapping("/agents")
    public Map<String, Object> agents() {
        return Map.of(
                "orchestrator", "意图识别 + 调度 + 汇总",
                "planner",      "学习路径规划",
                "quiz",         "练习题生成与判卷",
                "tutor",        "答疑解惑 (支持 RAG)",
                "recommender",  "学习资源推荐",
                "reviewer",     "学习进度复盘"
        );
    }
}
