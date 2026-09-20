package com.lms.catalog.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.PaymentStatus;
import com.lms.enrollment.entity.CourseReview;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang "Hiệu suất" của Giảng viên (19/09/2026, mở rộng ngoài đặc tả gốc — giao diện tham khảo
 * Udemy "Performance": Tổng quan/Doanh thu/Sinh viên/Đánh giá). Trước đây trang này chỉ là
 * placeholder "Coming Soon" ở FE, chưa có dữ liệu thật nào.
 *
 * <p>Tách riêng khỏi {@code DashboardController} (vốn xử lý logic ngay trong controller cho 2
 * endpoint cũ /admin, /instructor) — logic ở đây nhiều bước tính toán hơn (khoảng thời gian,
 * nhiều repository) nên theo đúng kiến trúc 3 tầng chuẩn của dự án (Controller → Service →
 * Repository) thay vì để thẳng trong controller.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final PaymentRepository paymentRepository;

    /** "7d"/"30d"/"12m"/"all" — quy ước tham số `range` dùng chung cho mọi endpoint bên dưới. */
    private LocalDateTime resolveRangeStart(String range) {
        LocalDateTime now = LocalDateTime.now();
        return switch (range == null ? "30d" : range) {
            case "7d" -> now.minusDays(7);
            case "12m" -> now.minusMonths(12);
            case "all" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> now.minusDays(30);
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceOverview(String email, String range) {
        LocalDateTime since = resolveRangeStart(range);
        BigDecimal revenue = paymentRepository.sumInstructorEarningSince(email, since);
        long enrollments = enrollmentRepository.countByCourse_Instructor_EmailAndEnrolledAtAfter(email, since);
        double avgRatingRaw = courseReviewRepository.findAverageRatingByInstructorEmailSince(email, since);
        BigDecimal avgRating = BigDecimal.valueOf(avgRatingRaw).setScale(2, RoundingMode.HALF_UP);

        return Map.of(
                "revenue", revenue,
                "enrollments", enrollments,
                "avgRating", avgRating
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRevenueList(String email, String range) {
        LocalDateTime since = resolveRangeStart(range);
        List<Payment> payments = paymentRepository
                .findByCourse_Instructor_EmailAndStatusAndPaidAtAfterOrderByPaidAtDesc(email, PaymentStatus.PAID, since);
        return payments.stream()
                .map(p -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("courseTitle", p.getCourse().getTitle());
                    row.put("amount", p.getAmount());
                    row.put("instructorEarning", p.getInstructorEarning());
                    row.put("paidAt", p.getPaidAt());
                    row.put("couponCode", p.getCoupon() != null ? p.getCoupon().getCode() : null);
                    // Chia doanh thu 2 mức (20/09/2026) — để Giảng viên phân biệt giao dịch nào
                    // đến từ liên kết giới thiệu riêng (97%) và tự tìm thấy trên nền tảng (37%).
                    row.put("revenueSource", p.getRevenueSource());
                    return row;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStudents(String email, Long courseId) {
        List<Enrollment> enrollments = enrollmentRepository.findByCourse_Instructor_EmailOrderByEnrolledAtDesc(email);
        return enrollments.stream()
                .filter(e -> courseId == null || e.getCourse().getId().equals(courseId))
                .map(e -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("studentId", e.getUser().getId());
                    row.put("studentName", e.getUser().getFullName());
                    row.put("studentEmail", e.getUser().getEmail());
                    row.put("courseId", e.getCourse().getId());
                    row.put("courseTitle", e.getCourse().getTitle());
                    row.put("enrolledAt", e.getEnrolledAt());
                    row.put("progressPct", e.getProgressPct());
                    return row;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReviews(String email, Long courseId) {
        List<CourseReview> reviews = courseReviewRepository.findByCourse_Instructor_EmailAndIsHiddenFalseOrderByCreatedAtDesc(email);
        return reviews.stream()
                .filter(r -> courseId == null || r.getCourse().getId().equals(courseId))
                .map(r -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("studentName", r.getUser().getFullName());
                    row.put("courseId", r.getCourse().getId());
                    row.put("courseTitle", r.getCourse().getTitle());
                    row.put("rating", r.getRating());
                    row.put("comment", r.getComment());
                    row.put("createdAt", r.getCreatedAt());
                    return row;
                })
                .toList();
    }

    /** Danh sách khóa học của giảng viên để đổ vào dropdown lọc ở trang "Học viên"/"Đánh giá". */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyCoursesForFilter(String email) {
        List<Course> courses = courseRepository.findByInstructor_Email(
                email, org.springframework.data.domain.Pageable.unpaged()).getContent();
        return courses.stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "title", c.getTitle()))
                .toList();
    }
}
