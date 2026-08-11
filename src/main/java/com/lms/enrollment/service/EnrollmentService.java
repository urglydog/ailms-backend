package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.entity.Payment;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Khóa học của tôi" — danh sách khóa Student đã sở hữu (UC12/UC14 tạo ra Enrollment; ở đây chỉ
 * đọc). Chưa có luồng ghi danh/mua khóa thật (Giai đoạn 3) nên module này hiện chỉ có phần đọc,
 * phục vụ trực tiếp F2.2 (Student cần biết mình đã sở hữu khóa nào để vào đánh giá — UC23).
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<Res> getMyEnrollments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return enrollmentRepository.findByUser_Email(email).stream()
                .map(enrollment -> mapToRes(user, enrollment))
                .toList();
    }

    private Res mapToRes(User user, Enrollment enrollment) {
        Course course = enrollment.getCourse();
        boolean alreadyReviewed = courseReviewRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId());
        return new Res(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getThumbnailUrl(),
                course.getCategory().getName(),
                course.getIsFree(),
                course.getPrice(),
                alreadyReviewed,
                enrollment.getProgressPct(),
                enrollment.getCompletedAt(),
                // BR-PROGRESS-04: Quiz thật làm ở Giai đoạn 7 — để null an toàn ở đây.
                null
        );
    }

    @Transactional
    public void enrollFreeCourse(String email, Long courseId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Chỉ có thể ghi danh khóa học đã xuất bản.");
        }
        if (!Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Khóa học không miễn phí. Yêu cầu thanh toán.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(user);
        enrollment.setCourse(course);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setProgressPct(BigDecimal.ZERO);
        enrollmentRepository.save(enrollment);
    }

    /**
     * Dùng cho luồng thanh toán thành công (F3.2 gọi sang).
     * @param payment Giao dịch đã được xác nhận PAID.
     */
    @Transactional
    public void createFromPayment(Payment payment) {
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId())) {
            return;
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setUser(payment.getUser());
        enrollment.setCourse(payment.getCourse());
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setProgressPct(BigDecimal.ZERO);
        enrollmentRepository.save(enrollment);
    }
}
