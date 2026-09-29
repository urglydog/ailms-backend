package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.CourseReviewDto.*;
import com.lms.enrollment.entity.CourseReview;
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

        if (!enrollmentRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new BusinessRuleViolationException(
                    "Bạn cần sở hữu khóa học này trước khi đánh giá (BR-ENROLL-01)");
        }
        if (courseReviewRepository.existsByUser_IdAndCourse_Id(student.getId(), courseId)) {
            throw new ConflictException("Bạn đã đánh giá khóa học này rồi");
        }

        CourseReview review = new CourseReview();
        review.setUser(student);
        review.setCourse(course);
        review.setRating(req.rating());
        review.setComment(req.comment());
        review.setIsHidden(false);
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

    private void pushAiReviewJob(Long reviewId, String text) {
        try {
            java.util.Map<String, Object> job = new java.util.HashMap<>();
            job.put("reviewId", reviewId);
            job.put("text", text);
            redisTemplate.opsForList().leftPush(reviewQueueKey, objectMapper.writeValueAsString(job));
            log.info("Da day job AI review moderation cho reviewId={}", reviewId);
        } catch (Exception e) {
            log.error("Loi day job review {} sang AI worker", reviewId, e);
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
        courseReviewRepository.save(review);
        recalcAvgRating(review.getCourse());
        cacheEvictionHelper.evictCourseCacheAfterCommit(review.getCourse().getSlug());
        log.info("AI Worker da an review {} voi ly do: {}", id, reason);
    }

    private Res setHidden(Long id, boolean hidden) {
        CourseReview review = courseReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseReview", id));
        review.setIsHidden(hidden);
        if (hidden) {
            review.setModerationReason("Ẩn thủ công bởi Admin");
        } else {
            review.setModerationReason(null);
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
                review.getCreatedAt()
        );
    }
}
