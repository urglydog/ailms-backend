package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.CourseReviewDto.*;
import com.lms.enrollment.entity.CourseReview;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.entity.ReviewModerationStatus;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.lms.common.util.CacheEvictionHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * Đánh giá khóa học (UC23) và Admin kiểm duyệt (UC44). Chỉ học viên đã sở hữu khóa mới được
 * đánh giá (BR-ENROLL-01), mỗi người 1 lần/khóa (UNIQUE ở tầng DB, kiểm trước để trả lỗi rõ ràng
 * thay vì để lộ {@code DataIntegrityViolationException}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseReviewService {

    private final CourseReviewRepository courseReviewRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheEvictionHelper cacheEvictionHelper;

    @Value("${lms.redis-keys.course-review-queue:lms:course-review:jobs}")
    private String reviewQueueKey;

    /** BUG THẬT (03/10/2026) — AC "chỉ tài khoản đã học >=20-30% khóa học mới được review" chưa
     * được code dù mục này đã bị đánh dấu ĐÃ HOÀN THÀNH (chỉ check đã enroll + chưa review). */
    @Value("${lms.rules.review-min-progress-percent}")
    private int minProgressPercentToReview;

    @Transactional(readOnly = true)
    public Page<Res> listForCourse(Long courseId, Pageable pageable) {
        return courseReviewRepository.findByCourse_IdAndIsHiddenFalse(courseId, pageable).map(this::mapToRes);
    }

    @Transactional
    public Res create(String studentEmail, Long courseId, CreateReq req) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", studentEmail));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        Enrollment enrollment = enrollmentRepository.findByUser_IdAndCourse_Id(student.getId(), courseId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Bạn cần sở hữu khóa học này trước khi đánh giá (BR-ENROLL-01)"));
        if (courseReviewRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new ConflictException("Bạn đã đánh giá khóa học này rồi");
        }
        if (enrollment.getProgressPct().compareTo(BigDecimal.valueOf(minProgressPercentToReview)) < 0) {
            throw new BusinessRuleViolationException(
                    "Bạn cần học ít nhất " + minProgressPercentToReview + "% khóa học trước khi đánh giá (hiện tại: "
                            + enrollment.getProgressPct().setScale(0, RoundingMode.HALF_UP) + "%)");
        }

        CourseReview review = new CourseReview();
        review.setUser(student);
        review.setCourse(course);
        review.setRating(req.rating());
        review.setComment(req.comment());
        review.setIsHidden(false);
        review.setModerationStatus(ReviewModerationStatus.VISIBLE);
        CourseReview saved = courseReviewRepository.save(review);

        recalcAvgRating(course);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushAiReviewJob(saved.getId(), saved.getComment());
                }
            });
        } else {
            pushAiReviewJob(saved.getId(), saved.getComment());
        }

        return mapToRes(saved);
    }

    /** BUG THẬT (03/10/2026) — trước đây lỗi push Redis chỉ log rồi NUỐT LUÔN, review mất mãi
     * cơ hội được AI kiểm duyệt (fail-open âm thầm, không retry). Thêm retry ngắn (bù lỗi mạng/
     * Redis chập chờn tức thời) — KHÔNG phải dead-letter queue đầy đủ, nhưng xử lý được phần lớn
     * lỗi thoáng qua thay vì mất job ngay lần đầu. */
    private static final int PUSH_AI_JOB_MAX_ATTEMPTS = 3;

    private void pushAiReviewJob(Long reviewId, String text) {
        java.util.Map<String, Object> job = new java.util.HashMap<>();
        job.put("reviewId", reviewId);
        job.put("text", text);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(job);
        } catch (Exception e) {
            log.error("Loi serialize job review {} sang AI worker, KHONG retry (loi serialize khong tu het)", reviewId, e);
            return;
        }

        for (int attempt = 1; attempt <= PUSH_AI_JOB_MAX_ATTEMPTS; attempt++) {
            try {
                redisTemplate.opsForList().leftPush(reviewQueueKey, payload);
                log.info("Da day job AI review moderation cho reviewId={} (lan thu {})", reviewId, attempt);
                return;
            } catch (Exception e) {
                if (attempt == PUSH_AI_JOB_MAX_ATTEMPTS) {
                    log.error("Loi day job review {} sang AI worker sau {} lan thu — review se KHONG duoc AI kiem duyet, can xu ly thu cong", reviewId, attempt, e);
                } else {
                    log.warn("Loi day job review {} sang AI worker (lan {}/{}), dang retry", reviewId, attempt, PUSH_AI_JOB_MAX_ATTEMPTS, e);
                    try {
                        Thread.sleep(200L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<Res> listAll(String courseTitle, String instructorEmail, String status, Pageable pageable) {
        String finalCourseTitle = (courseTitle == null || courseTitle.isBlank()) ? null : courseTitle;
        String finalInstructorEmail = (instructorEmail == null || instructorEmail.isBlank()) ? null : instructorEmail;
        String finalStatus = (status == null || status.isBlank()) ? null : status;
        return courseReviewRepository.searchAdminReviews(finalCourseTitle, finalInstructorEmail, finalStatus, pageable).map(this::mapToRes);
    }

    @Transactional
    public Res hide(Long id) {
        return setHidden(id, true);
    }

    @Transactional
    public Res unhide(Long id) {
        return setHidden(id, false);
    }

    @Transactional
    public void hideByAi(Long id, String reason) {
        CourseReview review = courseReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseReview", id));
        review.setIsHidden(true);
        review.setModerationReason(reason);
        review.setModerationStatus(ReviewModerationStatus.HIDDEN);
        courseReviewRepository.save(review);
        recalcAvgRating(review.getCourse());
        cacheEvictionHelper.evictCourseCacheAfterCommit(review.getCourse().getSlug());
        log.info("AI Worker da an review {} voi ly do: {}", id, reason);
    }

    /**
     * Refined AC (03/10/2026) — Giảng viên Report 1 review của khóa CỦA CHÍNH MÌNH: ẩn ngay
     * (giống tinh thần "ẩn thủ công"), chuyển {@code moderationStatus=PENDING_REPORT} để vào
     * hàng chờ Admin duyệt riêng (xem {@code listAll(status="PENDING_REPORT")}) — KHÁC với ẩn
     * do AI/Admin (status=HIDDEN, không cần duyệt lại).
     */
    @Transactional
    public Res reportByInstructor(Long id, String instructorEmail, String reason) {
        CourseReview review = courseReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseReview", id));
        if (!review.getCourse().getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn chỉ được report review trên khóa học của chính mình");
        }
        if (review.getModerationStatus() == ReviewModerationStatus.PENDING_REPORT) {
            throw new ConflictException("Review này đã được report và đang chờ Admin duyệt");
        }
        review.setIsHidden(true);
        review.setModerationStatus(ReviewModerationStatus.PENDING_REPORT);
        review.setModerationReason("Giảng viên report: " + (reason == null || reason.isBlank() ? "(không nêu lý do)" : reason.trim()));
        CourseReview saved = courseReviewRepository.save(review);
        recalcAvgRating(review.getCourse());
        cacheEvictionHelper.evictCourseCacheAfterCommit(review.getCourse().getSlug());
        return mapToRes(saved);
    }

    private Res setHidden(Long id, boolean hidden) {
        CourseReview review = courseReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseReview", id));
        review.setIsHidden(hidden);
        if (hidden) {
            review.setModerationReason("Ẩn thủ công bởi Admin");
            review.setModerationStatus(ReviewModerationStatus.HIDDEN);
        } else {
            review.setModerationReason(null);
            review.setModerationStatus(ReviewModerationStatus.VISIBLE);
        }
        CourseReview saved = courseReviewRepository.save(review);
        recalcAvgRating(review.getCourse());
        cacheEvictionHelper.evictCourseCacheAfterCommit(review.getCourse().getSlug());
        return mapToRes(saved);
    }

    /** BR: {@code Course.avgRating} tính lại mỗi khi có CourseReview thay đổi, loại trừ review đã ẩn. */
    private void recalcAvgRating(Course course) {
        Double average = courseReviewRepository.findAverageRatingByCourseId(course.getId());
        course.setAvgRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        courseRepository.save(course);
    }

    private Res mapToRes(CourseReview review) {
        return new Res(
                review.getId(),
                review.getCourse().getId(),
                review.getCourse().getTitle(),
                review.getUser().getFullName(),
                review.getUser().getAvatarUrl(),
                review.getRating(),
                review.getComment(),
                review.getIsHidden(),
                review.getModerationReason(),
                review.getCreatedAt(),
                review.getModerationStatus().name()
        );
    }
}
