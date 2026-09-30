package com.lms.material.repository;

import com.lms.material.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link QuizAttempt}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUser_EmailAndQuiz_IdOrderByScoreDesc(String email, Long quizId);

    // Đếm số lần làm bài đã nộp hoặc đang làm
    List<QuizAttempt> findByUser_EmailAndQuiz_Id(String email, Long quizId);

    // Tìm attempt đang làm dở gần nhất
    QuizAttempt findFirstByUser_EmailAndQuiz_IdAndStatusOrderByCreatedAtDesc(String email, Long quizId, String status);

    int countByQuiz_Id(Long quizId);

    /** UC-ANTICHEAT — màn hình "Giám sát thi" cho giảng viên. */
    List<QuizAttempt> findByQuiz_IdAndStatusOrderBySubmittedAtDesc(Long quizId, String status);

    List<QuizAttempt> findByUser_EmailAndQuiz_MaterialGeneration_Course_IdOrderBySubmittedAtDesc(String email, Long courseId);

    void deleteByQuiz_Id(Long quizId);

    /** UC-ANTICHEAT — khoá ghi (PESSIMISTIC_WRITE) khi đọc attempt để ghi nhận vi phạm/chấm điểm,
     * tránh lost-update khi nhiều request đồng thời (violation rời rạc từ FE + quét webcam định
     * kỳ) cùng đọc-cộng-ghi {@code violationCount} cho cùng 1 attempt. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM QuizAttempt a WHERE a.id = :id")
    Optional<QuizAttempt> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
        WITH RankedSubmissions AS (
            SELECT qa.*,
                   ROW_NUMBER() OVER (PARTITION BY qa.quiz_id ORDER BY qa.submitted_at DESC) as rn
            FROM quiz_attempts qa
            JOIN quizzes q ON qa.quiz_id = q.id
            JOIN material_generations mg ON q.material_generation_id = mg.id
            WHERE qa.user_id = :userId 
              AND mg.course_id = :courseId
              AND qa.submitted_at >= (CURRENT_DATE - INTERVAL 30 DAY)
              AND qa.status = 'COMPLETED'
        )
        SELECT * FROM RankedSubmissions rs WHERE rs.rn <= 5
    """, nativeQuery = true)
    List<QuizAttempt> findRecentTop5AttemptsPerQuiz(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
