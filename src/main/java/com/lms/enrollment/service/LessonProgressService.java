package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.LessonProgressDto.RecordReq;
import com.lms.enrollment.dto.LessonProgressDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.LessonProgress;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.repository.LessonProgressRepository;
import com.lms.enrollment.security.EnrollmentSecurity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC21 — ghi nhận tiến độ xem 1 bài học + cập nhật % tiến độ toàn khóa (UC22).
 *
 * <p>BR-PROGRESS-01: {@code watchedSec} là tổng thời gian phát THẬT, không cộng đoạn tua nhanh —
 * FE tự tính (chỉ cộng khi {@code timeupdate} liên tục, không nhảy cóc do {@code seeking}) và gửi
 * TÍCH LŨY mỗi lần; ở đây chỉ giữ {@code max(cũ, mới)}, vừa chống đếm trùng khi request gửi lại
 * do lỗi mạng, vừa tự nhiên đúng nghĩa "một chiều" mà không cần guard riêng.
 * {@code isCompleted} chuyển một chiều khi đạt ngưỡng — không bao giờ tự hủy khi xem lại.
 */
@Service
@RequiredArgsConstructor
public class LessonProgressService {

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final EnrollmentSecurity enrollmentSecurity;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    /** BR-PROGRESS-01 — ngưỡng % thời lượng để tính là đã hoàn thành 1 bài học. */
    @Value("${lms.rules.lesson-complete-threshold-percent}")
    private int completeThresholdPercent;

    @Transactional
    public Res recordProgress(String email, Long lessonId, RecordReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        // Chỉ học viên ĐÃ ghi danh mới tích lũy tiến độ — Preview không có "hoàn thành bài học"
        // (BR-ENROLL-02: khách xem thử không có Enrollment để cộng vào progressPct khóa học).
        if (!enrollmentSecurity.canAccessLesson(email, lessonId, false)) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này (BR-ENROLL-02)");
        }

        LessonProgress progress = lessonProgressRepository.findByUser_IdAndLesson_Id(user.getId(), lessonId)
                .orElseGet(() -> {
                    LessonProgress p = new LessonProgress();
                    p.setUser(user);
                    p.setLesson(lesson);
                    return p;
                });

        progress.setWatchedSec(Math.max(progress.getWatchedSec(), req.watchedSec()));
        progress.setLastPositionSec(req.lastPositionSec());

        boolean reachedThreshold = lesson.getDurationSec() > 0
                && progress.getWatchedSec() * 100L >= (long) lesson.getDurationSec() * completeThresholdPercent;
        if (reachedThreshold) {
            progress.setIsCompleted(true);
        }
        lessonProgressRepository.save(progress);

        Course course = lesson.getChapter().getCourse();
        recalculateEnrollmentProgress(user, course);

        return new Res(progress.getWatchedSec(), progress.getLastPositionSec(), progress.getIsCompleted());
    }

    /** UC22 — % = số bài COMPLETED / tổng số bài READY của khóa (BR-PROGRESS-02, xem TODO(doc) ở BR gốc). */
    private void recalculateEnrollmentProgress(User user, Course course) {
        Enrollment enrollment = enrollmentRepository.findByUser_IdAndCourse_Id(user.getId(), course.getId())
                .orElse(null);
        if (enrollment == null) {
            // Không có Enrollment (vd. Instructor xem trước bài của chính mình) — không có gì để cập nhật.
            return;
        }

        long totalReady = lessonRepository.countByChapter_CourseIdAndStatus(course.getId(), "READY");
        long completed = lessonProgressRepository
                .countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(user.getId(), course.getId());

        BigDecimal progressPct = totalReady == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReady), 2, RoundingMode.HALF_UP);
        enrollment.setProgressPct(progressPct);

        boolean isFullyComplete = totalReady > 0 && completed >= totalReady;
        if (isFullyComplete && enrollment.getCompletedAt() == null) {
            enrollment.setCompletedAt(LocalDateTime.now());
        }
        enrollmentRepository.save(enrollment);
    }
}
