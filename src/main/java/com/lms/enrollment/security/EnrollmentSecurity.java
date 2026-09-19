package com.lms.enrollment.security;

import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service kiểm tra quyền truy cập khóa học/bài học dựa trên Enrollment.
 * Dùng trực tiếp trong @PreAuthorize("@enrollmentSecurity.canAccessLesson(principal.name, #id, true)")
 */
@Service("enrollmentSecurity")
@RequiredArgsConstructor
public class EnrollmentSecurity {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    @Transactional(readOnly = true)
    public boolean canAccessCourse(String email, Long courseId) {
        if (email == null) return false;
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        // Giảng viên sở hữu khóa học -> luôn xem được, kể cả DRAFT/PENDING (19/09/2026 — nút
        // "Xem trước > Với tư cách là Giảng viên" ở trang chỉnh sửa khóa học cần truy cập được
        // NGAY khi vừa tạo, không cần đợi xuất bản/ghi danh).
        if (course.getInstructor() != null && course.getInstructor().getEmail().equals(email)) {
            return true;
        }

        // Học viên đã mua -> truy cập bất kể PUBLISHED hay ARCHIVED (BR-ENROLL-03)
        boolean hasEnrolled = enrollmentRepository.existsByUser_EmailAndCourse_Id(email, courseId);
        if (hasEnrolled) {
            return true;
        }

        // Nếu chưa mua, thì khoá phải PUBLISHED mới cho xem (hoặc học thử)
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return false;
        }

        return false; // Nếu khoá không free và chưa mua, return false
    }

    @Transactional(readOnly = true)
    public boolean canAccessLesson(String email, Long lessonId, boolean allowPreview) {
        if (email == null) return false;
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));

        Course course = lesson.getChapter().getCourse();
        Long courseId = course.getId();

        // Giảng viên sở hữu khóa học -> luôn xem được mọi bài học của khóa mình (cùng lý do với
        // `canAccessCourse` ở trên).
        if (course.getInstructor() != null && course.getInstructor().getEmail().equals(email)) {
            return true;
        }

        // Đã ghi danh -> luôn cho truy cập (ARCHIVED vẫn xem được - BR-ENROLL-03)
        boolean hasEnrolled = enrollmentRepository.existsByUser_EmailAndCourse_Id(email, courseId);
        if (hasEnrolled) {
            return true;
        }

        // Chưa ghi danh, khoá đang PUBLISHED
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return false;
        }

        // Xử lý logic Preview (BR-ENROLL-02)
        if (allowPreview && Boolean.TRUE.equals(lesson.getIsPreview())) {
            return true;
        }

        return false;
    }
}
