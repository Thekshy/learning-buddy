package com.learningbuddy.repositories;

import com.learningbuddy.models.LearningPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {
    List<LearningPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
}
