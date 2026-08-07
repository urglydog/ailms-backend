package com.lms.catalog.controller;

import com.lms.auth.repository.UserRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lms.auth.entity.User;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

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

    @GetMapping("/instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, Object>> getInstructorDashboard(java.security.Principal principal) {
        // Lấy thông tin user hiện tại (giảng viên)
        User instructor = userRepository.findByEmail(principal.getName()).orElseThrow();

        long myCourses = courseRepository.countByInstructor_Email(instructor.getEmail());
        // TODO: Chờ module Enrollment/Payment (Giai đoạn 3/9) để tính học viên/doanh thu thật
        long myStudents = 12450;
        double averageRating = 4.8;
        long revenue = 15400000;

        return ResponseEntity.ok(Map.of(
                "totalCourses", myCourses,
                "totalStudents", myStudents,
                "averageRating", averageRating,
                "revenue", revenue,
                "recentCourses", java.util.List.of(
                        Map.of("id", 1, "title", "React Masterclass", "status", "PUBLISHED", "students", 1250),
                        Map.of("id", 2, "title", "Advanced CSS Layouts", "status", "PENDING", "students", 0),
                        Map.of("id", 3, "title", "JavaScript for Beginners", "status", "DRAFT", "students", 0)
                )
        ));
    }
}
