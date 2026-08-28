package com.lms.material.repository;

import com.lms.material.entity.Quiz;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Quiz}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByMaterialGeneration_Id(Long id);
    Optional<Quiz> findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(Long courseId);
}
