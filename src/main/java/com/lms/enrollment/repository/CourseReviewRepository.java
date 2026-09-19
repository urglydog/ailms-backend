package com.lms.enrollment.repository;

import com.lms.enrollment.entity.CourseReview;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link CourseReview}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
@Repository
public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    /** "Khóa học của tôi" — số sao học viên tự chấm cho khóa, để hiển thị ngay trên thẻ card. */
    Optional<CourseReview> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    Page<CourseReview> findByCourse_IdAndIsHiddenFalse(Long courseId, Pageable pageable);

    long countByCourse_IdAndIsHiddenFalse(Long courseId);

    /**
     * Trung bình rating chưa bị ẩn của 1 khóa — trả 0 nếu chưa có đánh giá nào (Course.avgRating).
     * Kiểu trả về là {@code Double} vì AVG() trong JPQL luôn trả kiểu này cho cột Integer — ép
     * thẳng sang BigDecimal ở đây dễ vỡ lúc runtime tùy provider, nên convert ở tầng Service.
     */
    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM CourseReview r "
            + "WHERE r.course.id = :courseId AND r.isHidden = false")
    Double findAverageRatingByCourseId(@Param("courseId") Long courseId);

    /** Trang "Hiệu suất" > Tổng quan (19/09/2026, mở rộng) — điểm đánh giá trung bình của các
     * đánh giá GỬI TRONG khoảng thời gian chọn, trên mọi khóa của giảng viên. */
    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM CourseReview r "
            + "WHERE r.course.instructor.email = :email AND r.isHidden = false AND r.createdAt >= :after")
    Double findAverageRatingByInstructorEmailSince(@Param("email") String email, @Param("after") java.time.LocalDateTime after);

    /** Trang "Hiệu suất" > Đánh giá — toàn bộ đánh giá (không ẩn) trên các khóa của giảng viên. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "course"})
    java.util.List<CourseReview> findByCourse_Instructor_EmailAndIsHiddenFalseOrderByCreatedAtDesc(String email);
}
