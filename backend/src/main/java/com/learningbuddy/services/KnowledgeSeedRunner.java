package com.learningbuddy.services;

import com.learningbuddy.agents.AgentContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识树种子数据注入器
 *
 * <p>用 Java 端跑种子数据,避免外部脚本依赖。
 * <p>目前**不写关系库**——D2 阶段 JPA 实体按需生成时,这里再补 JPA 写入。
 * <p>当前阶段只打日志,便于演示时看到"3 门学科已就绪"。
 */
@Slf4j
@Component
@Order(1)
public class KnowledgeSeedRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("🌱 种子数据:3 门学科(暂以日志形式加载,实体类生成时切到 JPA 写入)");
        log.info("  - Python 编程");
        log.info("  - 高等数学");
        log.info("  - 英语(CET-4/6)");
    }

    /** 演示用:返回一张内置知识树给 Planner 用 */
    public static Map<String, List<String>> builtinTree() {
        return Map.of(
                "python",  List.of("Python 基础", "语法与变量", "函数", "装饰器", "面向对象"),
                "math",    List.of("微积分", "极限", "导数", "积分"),
                "english", List.of("词汇", "CET-4 词汇", "CET-6 词汇")
        );
    }
}
