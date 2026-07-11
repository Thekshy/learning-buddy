package com.learningbuddy.services;

import com.learningbuddy.models.KnowledgeNode;
import com.learningbuddy.models.Subject;
import com.learningbuddy.repositories.KnowledgeNodeRepository;
import com.learningbuddy.repositories.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识树种子数据注入器(JPA 持久化版)
 *
 * <p>启动时幂等写入 3 门学科 + 简化知识节点(按 code 查重,已存在则跳过)。
 * <p>KnowledgeProgress / Quiz 通过 knowledge_node_id 挂靠这些节点。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class KnowledgeSeedRunner implements CommandLineRunner {

    private final SubjectRepository subjectRepository;
    private final KnowledgeNodeRepository nodeRepository;

    /** 学科 code → (节点 code → 节点标题) */
    private static final Map<String, List<String[]>> SEED = Map.of(
            "python", List.of(
                    new String[]{"basics", "Python 基础"},
                    new String[]{"syntax", "语法与变量"},
                    new String[]{"function", "函数"},
                    new String[]{"decorator", "装饰器"},
                    new String[]{"oop", "面向对象"}
            ),
            "math", List.of(
                    new String[]{"calculus", "微积分"},
                    new String[]{"limit", "极限"},
                    new String[]{"derivative", "导数"},
                    new String[]{"integral", "积分"}
            ),
            "english", List.of(
                    new String[]{"vocab", "词汇"},
                    new String[]{"cet4", "CET-4 词汇"},
                    new String[]{"cet6", "CET-6 词汇"}
            )
    );

    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "python", "Python 编程",
            "math", "高等数学",
            "english", "英语(CET-4/6)"
    );

    @Override
    public void run(String... args) {
        int created = 0;
        for (var entry : SEED.entrySet()) {
            String code = entry.getKey();
            Subject subject = subjectRepository.findByCode(code).orElseGet(() -> {
                Subject s = Subject.builder()
                        .code(code)
                        .name(SUBJECT_NAMES.get(code))
                        .build();
                return subjectRepository.save(s);
            });

            int order = 0;
            for (String[] node : entry.getValue()) {
                boolean exists = nodeRepository.findBySubjectIdAndCode(subject.getId(), node[0]).isPresent();
                if (exists) continue;
                nodeRepository.save(KnowledgeNode.builder()
                        .subject(subject)
                        .code(node[0])
                        .title(node[1])
                        .difficulty(2)
                        .sortOrder(order++)
                        .build());
                created++;
            }
        }
        log.info("🌱 种子数据就绪:3 门学科 + 知识节点(本次新建 {} 个节点)", created);
    }

    /** 演示用:返回一张内置知识树给前端展示 */
    public static Map<String, List<String>> builtinTree() {
        return Map.of(
                "python",  List.of("Python 基础", "语法与变量", "函数", "装饰器", "面向对象"),
                "math",    List.of("微积分", "极限", "导数", "积分"),
                "english", List.of("词汇", "CET-4 词汇", "CET-6 词汇")
        );
    }
}
