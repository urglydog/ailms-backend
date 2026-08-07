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

/**
 * Đánh giá khóa học (UC23) và Admin kiểm duyệt (UC44). Chỉ học viên đã sở hữu khóa mới được
 * đánh giá (BR-ENROLL-01), mỗi người 1 lần/khóa (UNIQUE ở tầng DB, kiểm trước để trả lỗi rõ ràng
 * thay vì để lộ {@code DataIntegrityViolationException}).
 */
@Service
@RequiredArgsConstructor
public class CourseReviewService {

    private final CourseReviewRepository courseReviewRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

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

        return mapToRes(saved);
    }

    @Transactional(readOnly = true)
    public Page<Res> listAll(Pageable pageable) {
        return courseReviewRepository.findAll(pageable).map(this::mapToRes);
    }

    @Transactional
    public Res hide(Long id) {
        return setHidden(id, true);
    }

    @Transactional
    public Res unhide(Long id) {
        return setHidden(id, false);
    }

    private Res setHidden(Long id, boolean hidden) {
        CourseReview review = courseReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseReview", id));
        review.setIsHidden(hidden);
        CourseReview saved = courseReviewRepository.save(review);
        recalcAvgRating(review.getCourse());
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
                review.getCreatedAt()
        );
    }
}
