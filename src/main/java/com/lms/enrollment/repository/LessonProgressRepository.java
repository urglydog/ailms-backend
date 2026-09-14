package com.lms.enrollment.repository;

import com.lms.enrollment.entity.LessonProgress;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link LessonProgress}.
 */
@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    /** UC16/17 — khoi phuc lastPositionSec khi hoc vien quay lai bai hoc (BR-PROGRESS-03). */
    Optional<LessonProgress> findByUser_IdAndLesson_Id(Long userId, Long lessonId);

    /** BR-PROGRESS-02 — dem so bai COMPLETED cua 1 hoc vien trong 1 khoa de tinh Enrollment.progressPct. */
    long countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(Long userId, Long courseId);

    /**
     * "Khóa học của tôi" — sort "Recently Accessed". Không có cột riêng lưu lần xem gần nhất,
     * tái dùng {@code updatedAt} (BaseEntity) của bản ghi tiến độ được cập nhật mỗi lần học viên
     * xem video (BR-PROGRESS-01/03) — MAX qua mọi bài trong khóa = lần học viên chạm khóa này
     * gần nhất. Null nếu học viên chưa xem bài nào trong khóa (mới ghi danh, chưa mở bài học).
     */
    @Query("SELECT MAX(lp.updatedAt) FROM LessonProgress lp "
            + "WHERE lp.user.id = :userId AND lp.lesson.chapter.course.id = :courseId")
    LocalDateTime findLastAccessedAtByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
