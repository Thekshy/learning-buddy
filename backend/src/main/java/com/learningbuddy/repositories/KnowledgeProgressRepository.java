package com.learningbuddy.repositories;

import com.learningbuddy.models.KnowledgeProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeProgressRepository extends JpaRepository<KnowledgeProgress, Long> {

    Optional<KnowledgeProgress> findByUserIdAndKnowledgeNodeId(Long userId, Long nodeId);

    /** 某用户所有知识点的掌握度(雷达图数据源) */
    List<KnowledgeProgress> findByUserId(Long userId);
}
