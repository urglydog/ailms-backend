package com.lms.chat.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.CourseReviewDto;
import com.lms.enrollment.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalInstructorAiService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseReviewService courseReviewService;

    @Transactional(readOnly = true)
    public Map<String, Object> getInstructorDashboard(String instructorEmail) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorEmail));

        long myCourses = courseRepository.countByInstructor_Email(instructor.getEmail());
        long myStudents = 12450;
        double averageRating = 4.8;
        long revenue = 15400000;

        return Map.of(
                "totalCourses", myCourses,
                "totalStudents", myStudents,
                "averageRating", averageRating,
                "revenue", revenue,
                "recentCourses", java.util.List.of(
                        Map.of("id", 1, "title", "React Masterclass", "status", "PUBLISHED", "students", 1250),
                        Map.of("id", 2, "title", "Advanced CSS Layouts", "status", "PENDING", "students", 0),
                        Map.of("id", 3, "title", "JavaScript for Beginners", "status", "DRAFT", "students", 0)
                )
        );
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
