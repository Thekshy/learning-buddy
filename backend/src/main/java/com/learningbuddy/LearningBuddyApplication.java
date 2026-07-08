package com.learningbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能学习伙伴(多智能体) — Spring Boot 入口
 *
 * <p>架构:Orchestrator 协调 + 5 个专职 Agent + LLM (MiniMax M3) + zvec RAG
 */
@SpringBootApplication
@EnableAsync
public class LearningBuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningBuddyApplication.class, args);
    }
}
