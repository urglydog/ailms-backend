package com.lms.material.repository;

import com.lms.material.entity.QuizAttemptViolation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptViolationRepository extends JpaRepository<QuizAttemptViolation, Long> {
    List<QuizAttemptViolation> findByAttempt_IdOrderByCreatedAtAsc(Long attemptId);
    long countByAttempt_Id(Long attemptId);
}
