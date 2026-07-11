package com.learningbuddy.repositories;

import com.learningbuddy.models.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /** 某用户最近 N 次作答(Reviewer 复盘数据源) */
    List<Attempt> findTop20ByUserIdOrderBySubmittedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndIsCorrectTrue(Long userId);
}
