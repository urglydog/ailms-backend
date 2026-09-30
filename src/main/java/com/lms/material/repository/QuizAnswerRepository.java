package com.lms.material.repository;

import com.lms.material.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link QuizAnswer}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {
    List<QuizAnswer> findByQuizAttempt_Id(Long attemptId);
    void deleteByQuizAttempt_Quiz_Id(Long quizId);

    /** Sprint 3 mục 10 — Top câu hỏi có tỷ lệ sai > 60%, CHỈ trong các khóa của đúng
     * :instructorEmail đang gọi (RBAC — không lộ dữ liệu quiz của giảng viên khác).
     *
     * <p><b>{@code HAVING COUNT(qa) >= 5} bắt buộc</b> (ngưỡng mẫu tối thiểu, chống thiên lệch
     * cỡ mẫu nhỏ) — nếu không, 1 câu hỏi mới toanh chỉ có đúng 1 học viên làm và trả lời sai sẽ
     * có tỷ lệ sai 100%, nhảy lên đầu bảng xếp hạng dù không đại diện gì cả.
     *
     * <p>Trả từng dòng: [questionId, content, courseId, courseTitle, lessonTitle (có thể null —
     * quiz cấp khóa không gắn 1 bài học cụ thể), materialGenerationId (để FE deep-link thẳng vào
     * ô "inspect" có sẵn ở trang Quản lý học liệu — xem `CourseMaterialsManager.tsx`), totalAnswers,
     * wrongCount]. */
    @Query("SELECT qq.id, qq.content, mg.course.id, mg.course.title, l.title, mg.id, COUNT(qa), "
            + "SUM(CASE WHEN qa.isCorrect = false THEN 1L ELSE 0L END) "
            + "FROM QuizAnswer qa "
            + "JOIN qa.quizQuestion qq "
            + "JOIN qq.quiz q "
            + "JOIN q.materialGeneration mg "
            + "LEFT JOIN mg.lesson l "
            + "WHERE mg.course.instructor.email = :instructorEmail "
            + "GROUP BY qq.id, qq.content, mg.course.id, mg.course.title, l.title, mg.id "
            + "HAVING COUNT(qa) >= 5 AND (SUM(CASE WHEN qa.isCorrect = false THEN 1L ELSE 0L END) * 1.0 / COUNT(qa)) > 0.6 "
            + "ORDER BY (SUM(CASE WHEN qa.isCorrect = false THEN 1L ELSE 0L END) * 1.0 / COUNT(qa)) DESC")
    List<Object[]> findHardQuestionsByInstructor(@Param("instructorEmail") String instructorEmail);
}
