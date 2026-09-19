package com.lms.catalog.controller;

import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.repository.PaymentRepository;
import com.lms.catalog.service.DashboardService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lms.auth.entity.User;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final DashboardService dashboardService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        long totalCourses = courseRepository.count();
        long totalUsers = userRepository.count();
        long pendingCourses = courseRepository.countByStatus(CourseStatus.PENDING);
        // TODO: Chờ module Payment (Giai đoạn 3/9) để tính doanh thu thật
        long totalRevenue = 15400000;

        return ResponseEntity.ok(Map.of(
                "totalCourses", totalCourses,
                "totalUsers", totalUsers,
                "pendingCourses", pendingCourses,
                "totalRevenue", totalRevenue
        ));
    }

    @GetMapping("/admin/system")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;

        java.io.File root = new java.io.File("/");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;

        return ResponseEntity.ok(Map.of(
                "ram", Map.of(
                        "used", usedMemory,
                        "total", maxMemory
                ),
                "disk", Map.of(
                        "used", usedSpace,
                        "total", totalSpace
                ),
                "uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()
        ));
    }

    /**
     * (15/09/2026, sửa lỗi) — trước đây `myStudents`/`averageRating`/`revenue` gắn cứng
     * (12450/4.8/15.400.000đ) với ghi chú "chờ module Enrollment/Payment", nhưng 2 module đó
     * đã có thật từ lâu trong dự án — lỗi này lộ rõ khi 1 tài khoản MỚI vừa "Trở thành Giảng
     * viên" (0 khóa học) vẫn thấy số liệu giả của người khác. Tính lại bằng dữ liệu thật.
     */
    @GetMapping("/instructor")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, Object>> getInstructorDashboard(java.security.Principal principal) {
        User instructor = userRepository.findByEmail(principal.getName()).orElseThrow();
        String email = instructor.getEmail();

        long myCourses = courseRepository.countByInstructor_Email(email);
        long myStudents = enrollmentRepository.countByCourse_Instructor_Email(email);

        List<Course> courses = courseRepository.findByInstructor_Email(email, Pageable.unpaged()).getContent();
        double averageRating = courses.stream()
                .map(Course::getAvgRating)
                .filter(rating -> rating != null && rating.compareTo(BigDecimal.ZERO) > 0)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
        averageRating = BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP).doubleValue();

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal revenue = paymentRepository.sumInstructorEarningSince(email, monthStart);

        return ResponseEntity.ok(Map.of(
                "totalCourses", myCourses,
                "totalStudents", myStudents,
                "averageRating", averageRating,
                "revenue", revenue
        ));
    }

    // ==================== Trang "Hiệu suất" (19/09/2026, mở rộng) ====================
    // Trước đây chỉ là "Coming Soon" ở FE — logic tính toán nằm ở DashboardService (nhiều
    // bước hơn 2 endpoint cũ ở trên nên tách hẳn theo đúng kiến trúc 3 tầng của dự án).

    @GetMapping("/instructor/performance")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, Object>> getPerformanceOverview(
            java.security.Principal principal, @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(dashboardService.getPerformanceOverview(principal.getName(), range));
    }

    @GetMapping("/instructor/revenue")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Map<String, Object>>> getRevenueList(
            java.security.Principal principal, @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(dashboardService.getRevenueList(principal.getName(), range));
    }

    @GetMapping("/instructor/students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Map<String, Object>>> getStudents(
            java.security.Principal principal, @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(dashboardService.getStudents(principal.getName(), courseId));
    }

    @GetMapping("/instructor/reviews")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Map<String, Object>>> getReviews(
            java.security.Principal principal, @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(dashboardService.getReviews(principal.getName(), courseId));
    }

    @GetMapping("/instructor/my-courses")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Map<String, Object>>> getMyCoursesForFilter(java.security.Principal principal) {
        return ResponseEntity.ok(dashboardService.getMyCoursesForFilter(principal.getName()));
    }
}
