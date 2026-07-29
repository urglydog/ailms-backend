package com.lms.material.repository;

import com.lms.material.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link QuizQuestion}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
}
