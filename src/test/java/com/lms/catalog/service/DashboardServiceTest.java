package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Category;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Trang "Hiệu suất" Giảng viên (19/09/2026, mở rộng ngoài đặc tả gốc — giao diện tham khảo
 * Udemy). Kiểm tra khoảng thời gian (7d/30d/12m/all) truyền đúng xuống repository, và các danh
 * sách doanh thu/học viên/đánh giá lọc đúng theo `courseId` khi có truyền.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String EMAIL = "instructor@lms.local";

    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User instructor;
    private Course courseA;
    private Course courseB;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(1L);
        instructor.setEmail(EMAIL);
        instructor.setFullName("Cô Lan");

        Category category = new Category();
        category.setId(1L);
        category.setName("Lập trình");

        courseA = new Course();
        courseA.setId(10L);
        courseA.setTitle("Khóa A");
        courseA.setInstructor(instructor);
        courseA.setCategory(category);

        courseB = new Course();
        courseB.setId(20L);
        courseB.setTitle("Khóa B");
        courseB.setInstructor(instructor);
        courseB.setCategory(category);

        lenient().when(paymentRepository.sumInstructorEarningSince(eq(EMAIL), any())).thenReturn(new BigDecimal("500000"));
        lenient().when(enrollmentRepository.countByCourse_Instructor_EmailAndEnrolledAtAfter(eq(EMAIL), any())).thenReturn(3L);
        lenient().when(courseReviewRepository.findAverageRatingByInstructorEmailSince(eq(EMAIL), any())).thenReturn(4.5);
    }

    @Test
    void getPerformanceOverview_returnsAggregatedMetrics() {
        Map<String, Object> result = dashboardService.getPerformanceOverview(EMAIL, "30d");

        assertThat(result.get("revenue")).isEqualTo(new BigDecimal("500000"));
        assertThat(result.get("enrollments")).isEqualTo(3L);
        assertThat(result.get("avgRating")).isEqualTo(new BigDecimal("4.50"));
    }

    @Test
    void getPerformanceOverview_allRange_usesFarPastCutoff() {
        dashboardService.getPerformanceOverview(EMAIL, "all");

        org.mockito.ArgumentCaptor<LocalDateTime> captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(paymentRepository).sumInstructorEarningSince(eq(EMAIL), captor.capture());
        assertThat(captor.getValue().getYear()).isLessThanOrEqualTo(2000);
    }

    @Test
    void getPerformanceOverview_7dRange_usesRecentCutoff() {
        dashboardService.getPerformanceOverview(EMAIL, "7d");

        org.mockito.ArgumentCaptor<LocalDateTime> captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(paymentRepository).sumInstructorEarningSince(eq(EMAIL), captor.capture());
        assertThat(captor.getValue()).isAfter(LocalDateTime.now().minusDays(8));
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    @Test
    void getRevenueList_mapsPaymentFieldsToRows() {
        Payment payment = new Payment();
        payment.setCourse(courseA);
        payment.setAmount(new BigDecimal("100000"));
        payment.setInstructorEarning(new BigDecimal("70000"));
        payment.setPaidAt(LocalDateTime.now());
        when(paymentRepository.findByCourse_Instructor_EmailAndStatusAndPaidAtAfterOrderByPaidAtDesc(
                eq(EMAIL), eq(PaymentStatus.PAID), any())).thenReturn(List.of(payment));

        List<Map<String, Object>> result = dashboardService.getRevenueList(EMAIL, "30d");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("courseTitle")).isEqualTo("Khóa A");
        assertThat(result.get(0).get("instructorEarning")).isEqualTo(new BigDecimal("70000"));
    }

    @Test
    void getStudents_withoutCourseFilter_returnsAll() {
        Enrollment e1 = enrollmentOf(courseA);
        Enrollment e2 = enrollmentOf(courseB);
        when(enrollmentRepository.findByCourse_Instructor_EmailOrderByEnrolledAtDesc(EMAIL)).thenReturn(List.of(e1, e2));

        List<Map<String, Object>> result = dashboardService.getStudents(EMAIL, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void getStudents_withCourseFilter_onlyMatchingCourse() {
        Enrollment e1 = enrollmentOf(courseA);
        Enrollment e2 = enrollmentOf(courseB);
        when(enrollmentRepository.findByCourse_Instructor_EmailOrderByEnrolledAtDesc(EMAIL)).thenReturn(List.of(e1, e2));

        List<Map<String, Object>> result = dashboardService.getStudents(EMAIL, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("courseTitle")).isEqualTo("Khóa A");
    }

    @Test
    void getReviews_withCourseFilter_onlyMatchingCourse() {
        CourseReview r1 = reviewOf(courseA);
        CourseReview r2 = reviewOf(courseB);
        when(courseReviewRepository.findByCourse_Instructor_EmailAndIsHiddenFalseOrderByCreatedAtDesc(EMAIL))
                .thenReturn(List.of(r1, r2));

        List<Map<String, Object>> result = dashboardService.getReviews(EMAIL, 20L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("courseTitle")).isEqualTo("Khóa B");
    }

    @Test
    void getMyCoursesForFilter_mapsIdAndTitle() {
        when(courseRepository.findByInstructor_Email(eq(EMAIL), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(courseA, courseB)));

        List<Map<String, Object>> result = dashboardService.getMyCoursesForFilter(EMAIL);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("title")).isEqualTo("Khóa A");
    }

    private Enrollment enrollmentOf(Course course) {
        Enrollment e = new Enrollment();
        e.setUser(instructor);
        e.setCourse(course);
        e.setEnrolledAt(LocalDateTime.now());
        e.setProgressPct(BigDecimal.ZERO);
        return e;
    }

    private CourseReview reviewOf(Course course) {
        CourseReview r = new CourseReview();
        r.setUser(instructor);
        r.setCourse(course);
        r.setRating(5);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }
}
