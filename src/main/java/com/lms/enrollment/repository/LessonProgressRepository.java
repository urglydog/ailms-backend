package com.lms.enrollment.repository;

import com.lms.enrollment.entity.LessonProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
