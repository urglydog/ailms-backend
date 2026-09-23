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
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizAttempt;
import com.lms.material.repository.QuizAttemptRepository;
import com.lms.material.repository.QuizRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    /** A2 (UpComming_Plan.md) — ngưỡng "đạt" Quiz, tái dùng đúng quy ước đã có ở Gradebook
     * (thang điểm 0-10, xem InstructorGradebookController) — không bịa ngưỡng mới. */
    private static final java.math.BigDecimal QUIZ_PASS_SCORE = new java.math.BigDecimal("5.00");

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

    /**
     * UC22 — % tiến độ khóa học (BR-PROGRESS-02).
     *
     * <p>A2 (UpComming_Plan.md, 23/09/2026) — chuẩn hóa theo quyết định đã chốt với người dùng:
     * 70% video (bài COMPLETED / tổng bài READY) + 30% Quiz chính thức của khóa (đạt khi điểm cao
     * nhất ≥ {@link #QUIZ_PASS_SCORE}, cùng ngưỡng "đạt" đã dùng ở Gradebook — không bịa ngưỡng
     * mới). Khóa KHÔNG có Quiz chính thức thì dồn 100% trọng số vào video (không phạt học viên vì
     * thiếu thứ giảng viên chưa tạo). Học liệu tĩnh (tài nguyên PDF/slide toàn khóa) KHÔNG tính
     * vào % — chỉ để tham khảo, theo đúng lựa chọn đã chốt.
     *
     * <p>Enrollment ĐÃ hoàn thành (completedAt cũ, tính theo công thức trước đây) được giữ
     * nguyên 100% — công thức mới chỉ áp dụng cho lần tính tiếp theo của enrollment CHƯA hoàn
     * thành, không hồi tố. Gọi lại được từ cả sự kiện xem video (đây) lẫn sự kiện nộp Quiz
     * (xem {@code QuizService.submitAttempt}), nên phải public.
     */
    public void recalculateEnrollmentProgress(User user, Course course) {
        Enrollment enrollment = enrollmentRepository.findByUser_IdAndCourse_Id(user.getId(), course.getId())
                .orElse(null);
        if (enrollment == null) {
            // Không có Enrollment (vd. Instructor xem trước bài của chính mình) — không có gì để cập nhật.
            return;
        }
        if (enrollment.getCompletedAt() != null) {
            // Đã hoàn thành trước đó (kể cả theo công thức cũ) — giữ nguyên progressPct/completedAt,
            // không áp công thức mới (tránh tụt dưới 100% chỉ vì chưa có Quiz theo chuẩn mới).
            enrollmentRepository.save(enrollment);
            return;
        }

        long totalReady = lessonRepository.countByChapter_CourseIdAndStatus(course.getId(), "READY");
        long completed = lessonProgressRepository
                .countByUser_IdAndLesson_Chapter_Course_IdAndIsCompletedTrue(user.getId(), course.getId());

        BigDecimal videoPct = totalReady == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReady), 2, RoundingMode.HALF_UP);

        Optional<Quiz> officialQuiz = quizRepository
                .findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc(course.getId());

        BigDecimal progressPct;
        if (officialQuiz.isEmpty()) {
            progressPct = videoPct;
        } else {
            List<QuizAttempt> attempts = quizAttemptRepository
                    .findByUser_EmailAndQuiz_IdOrderByScoreDesc(user.getEmail(), officialQuiz.get().getId());
            boolean hasPassed = !attempts.isEmpty() && attempts.get(0).getScore().compareTo(QUIZ_PASS_SCORE) >= 0;
            BigDecimal quizPct = hasPassed ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
            progressPct = videoPct.multiply(new BigDecimal("0.7"))
                    .add(quizPct.multiply(new BigDecimal("0.3")))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        boolean isFullyComplete = totalReady > 0
                && completed >= totalReady
                && (officialQuiz.isEmpty() || progressPct.compareTo(BigDecimal.valueOf(100)) >= 0);
        if (isFullyComplete) {
            progressPct = BigDecimal.valueOf(100);
        }
        enrollment.setProgressPct(progressPct);

        if (isFullyComplete && enrollment.getCompletedAt() == null) {
            enrollment.setCompletedAt(LocalDateTime.now());
            enrollment.setCertificateCode(java.util.UUID.randomUUID().toString());
        }
        enrollmentRepository.save(enrollment);
    }
}
