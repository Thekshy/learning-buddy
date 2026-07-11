package com.learningbuddy.repositories;

import com.learningbuddy.models.KnowledgeNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, Long> {

    /** 按 subject + code 定位节点(幂等 seed 用) */
    Optional<KnowledgeNode> findBySubjectIdAndCode(Long subjectId, String code);

    List<KnowledgeNode> findBySubjectIdOrderBySortOrderAsc(Long subjectId);

    /** 模糊匹配标题,给 generateQuiz / planLearningPath 找挂靠点用 */
    Optional<KnowledgeNode> findFirstByTitleContaining(String titleFragment);
}
