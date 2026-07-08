package com.learningbuddy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 上下文加载冒烟测试(不需要外部 LLM 调用)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=sk-test",
        "learningbuddy.llm.fallback-mock=true",
        "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
})
class LearningBuddyApplicationTests {

    @Test
    void contextLoads() {
    }
}
