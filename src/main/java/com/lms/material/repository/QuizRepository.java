package com.lms.material.repository;

import com.lms.material.entity.Quiz;
import java.util.List;
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
    Optional<Quiz> findByMaterialGeneration_IdAndIsDeletedFalse(Long id);
    Optional<Quiz> findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(Long courseId);

    /** UC-ANTICHEAT — màn hình "Giám sát thi" cho giảng viên (danh sách quiz có bật giám sát). */
    List<Quiz> findByMaterialGeneration_Course_IdAndIsProctoredTrue(Long courseId);
}
