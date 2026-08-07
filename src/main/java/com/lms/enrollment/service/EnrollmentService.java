package com.lms.enrollment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
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
                alreadyReviewed
        );
    }
}
