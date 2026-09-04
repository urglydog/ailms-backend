package com.lms.enrollment.repository;

import com.lms.enrollment.entity.Enrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Enrollment}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** Dùng bởi CourseService (F2.1) để quyết định xoá cứng hay xoá mềm (BR-COURSE-03). */
    boolean existsByCourseId(Long courseId);

    /** Dùng bởi CourseReviewService (F2.2) để kiểm điều kiện sở hữu trước khi đánh giá (BR-ENROLL-01). */
    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByUser_EmailAndCourse_Id(String email, Long courseId);

    /** Danh sách khóa đã sở hữu của 1 học viên ("Khóa học của tôi"). */
    List<Enrollment> findByUser_Email(String email);

    /** UC21 — nạp entity để cập nhật `progressPct`/`completedAt` sau khi ghi nhận tiến độ 1 bài. */
    Optional<Enrollment> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    /** Danh sách ghi danh của một khóa học phục vụ Giảng viên Gradebook. */
    List<Enrollment> findByCourseId(Long courseId);

}
