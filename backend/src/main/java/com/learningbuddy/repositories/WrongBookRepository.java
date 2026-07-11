package com.learningbuddy.repositories;

import com.learningbuddy.models.WrongBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WrongBookRepository extends JpaRepository<WrongBook, Long> {

    Optional<WrongBook> findByUserIdAndQuestionId(Long userId, Long questionId);

    List<WrongBook> findByUserIdAndMasterLevelLessThanOrderByUpdatedAtDesc(Long userId, int masterLevel);
}
