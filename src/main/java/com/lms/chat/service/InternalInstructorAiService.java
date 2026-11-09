package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.service.DashboardService;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.CourseReviewDto;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalInstructorAiService {

    private static final int RECENT_COURSES_LIMIT = 5;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseReviewService courseReviewService;
    private final DashboardService dashboardService;

    /**
     * BUG THẬT (25/09/2026, GV báo cáo hỏi tình trạng khóa Unity nhưng AI luôn trả lời "React
     * Masterclass/Advanced CSS Layouts/JavaScript for Beginners" — 3 khóa KHÔNG hề tồn tại trong
     * DB thật): hàm này trước đây trả về số liệu HARDCODE cứng (myStudents=12450,
     * averageRating=4.8, revenue=15400000, recentCourses là danh sách bịa) — chỉ riêng
     * `totalCourses` là query thật. Tính năng "AI Trợ lý Giảng dạy" nhìn giống đã nối dây đầy đủ
     * (auth thật, HTTP call thật, callback thật) nên không ai phát hiện lớp dữ liệu bên dưới vẫn
     * là mock, đúng kiểu "vỏ rỗng" như Anti-Cheat/Auto-ban/Discovery từng bị trước khi nâng cấp.
     *
     * <p>Sửa: tái dùng đúng {@link DashboardService} (đã có query thật cho trang "Hiệu suất" của
     * GV, kiểm chứng qua UI từ trước) thay vì viết lại logic tính doanh thu/đánh giá riêng.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInstructorDashboard(String instructorEmail, String period) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorEmail));

        Map<String, Object> perf = dashboardService.getPerformanceOverview(instructor.getEmail(), mapPeriodToRange(period));
        long myCourses = courseRepository.countByInstructor_Email(instructor.getEmail());

        List<Course> recent = courseRepository
                .findByInstructor_Email(instructor.getEmail(),
                        PageRequest.of(0, RECENT_COURSES_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        List<Map<String, Object>> recentCourses = recent.stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "title", c.getTitle(),
                        "status", c.getStatus().name(),
                        "students", enrollmentRepository.countByCourseId(c.getId())
                ))
                .toList();

        return Map.of(
                "totalCourses", myCourses,
                "totalStudents", perf.get("enrollments"),
                "averageRating", perf.get("avgRating"),
                // Đặt tên "totalRevenue" khớp đúng field FE (`InstructorChat.tsx::CourseStatsData`)
                // đã đọc từ trước — mock cũ dùng "revenue" nên card "Doanh thu" luôn hiện trống.
                "totalRevenue", perf.get("revenue"),
                "recentCourses", recentCourses
        );
    }

    /** Tool `get_my_course_stats.period` (ai-worker) dùng vocab khác trang "Hiệu suất"
     * ({@link DashboardService} dùng 7d/30d/12m/all) — quy đổi hợp lý, tái dùng đúng query có
     * sẵn thay vì viết lại. this_month/last_month không có ranh giới tháng lịch chính xác trong
     * DashboardService nên tạm quy về "30d" (gần đúng, đủ cho câu trả lời tổng quan của AI).
     */
    private String mapPeriodToRange(String period) {
        return switch (period == null ? "all_time" : period) {
            case "this_month", "last_month" -> "30d";
            case "this_year" -> "12m";
            default -> "all";
        };
    }

    @Transactional(readOnly = true)
    public Page<CourseReviewDto.Res> listForCourse(String instructorEmail, Long courseId, Pageable pageable) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Giảng viên không có quyền truy cập đánh giá của khóa học này");
        }

        return courseReviewService.listForCourse(courseId, pageable);
    }
}
