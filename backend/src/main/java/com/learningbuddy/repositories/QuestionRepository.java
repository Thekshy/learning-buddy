package com.learningbuddy.repositories;

import com.learningbuddy.models.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuizIdOrderBySortOrderAsc(Long quizId);
}
