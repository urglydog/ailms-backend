package com.lms.enrollment.repository;

import org.springframework.data.jpa.repository.EntityGraph;
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

    /** UC10 mở rộng (14/09/2026) — số học viên hiển thị ở trang chi tiết khóa kiểu Udemy. */
    long countByCourseId(Long courseId);

    /** Dùng bởi CourseReviewService (F2.2) để kiểm điều kiện sở hữu trước khi đánh giá (BR-ENROLL-01). */
    boolean existsByUser_IdAndCourse_Id(Long userId, Long courseId);

    boolean existsByUser_EmailAndCourse_Id(String email, Long courseId);

    Optional<Enrollment> findByUser_EmailAndCourse_Id(String email, Long courseId);

    /** Danh sách khóa đã sở hữu của 1 học viên ("Khóa học của tôi"). */
    List<Enrollment> findByUser_Email(String email);

    /** "View public profile" (14/09/2026, mở rộng) — xem theo id thay vì email khi xem hồ sơ người KHÁC. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"course"})
    List<Enrollment> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** UC21 — nạp entity để cập nhật `progressPct`/`completedAt` sau khi ghi nhận tiến độ 1 bài. */
    Optional<Enrollment> findByUser_IdAndCourse_Id(Long userId, Long courseId);

    /** Danh sách ghi danh của một khóa học phục vụ Giảng viên Gradebook. */
    List<Enrollment> findByCourseId(Long courseId);

    /** Dashboard Giảng viên (15/09/2026, sửa lỗi) — tổng lượt ghi danh trên MỌI khóa của
     * giảng viên, thay số "12450" gắn cứng cũ (đang chờ module Enrollment — module đã có thật). */
    long countByCourse_Instructor_Email(String email);

    /** Trang "Hiệu suất" (19/09/2026, mở rộng) — lượt ghi danh MỚI trong khoảng thời gian chọn
     * (7 ngày/30 ngày/12 tháng/mọi thời điểm), khác {@link #countByCourse_Instructor_Email}
     * (tổng TOÀN BỘ, không lọc thời gian). */
    long countByCourse_Instructor_EmailAndEnrolledAtAfter(String email, java.time.LocalDateTime after);

    /** Trang "Học viên" — toàn bộ lượt ghi danh trên các khóa của giảng viên, mới nhất trước. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "course"})
    List<Enrollment> findByCourse_Instructor_EmailOrderByEnrolledAtDesc(String email);

    /** "Giao tiếp > Tin nhắn" (19/09/2026) — Giảng viên chỉ được nhắn tin cho học viên đã ghi
     * danh ÍT NHẤT 1 khóa của MÌNH, dùng khi không chọn ngữ cảnh khóa cụ thể lúc bắt đầu hội
     * thoại (tránh spam học viên hoàn toàn xa lạ). */
    boolean existsByUser_IdAndCourse_Instructor_Email(Long userId, String instructorEmail);

    /**
     * "Khóa học của tôi" — nạp sẵn course + category + instructor trong CÙNG 1 truy vấn (tránh N+1 khi
     * map từng thẻ khóa học). Các số liệu phụ lấy theo lô ở {@code EnrollmentService.getMyEnrollments}.
     */
    @EntityGraph(attributePaths = {"course", "course.category", "course.instructor"})
    List<Enrollment> findWithCourseByUser_Email(String email);


    /**
     * Thu hồi MỀM ghi danh khi hoàn tiền (11/11/2026). Native vì entity có {@code @SQLRestriction}.
     * Trả số dòng đã thu hồi (0 nếu học viên không còn ghi danh đang hiệu lực — idempotent).
     */
    // KHÔNG clearAutomatically: câu lệnh native chỉ đụng bảng enrollments, còn clear() sẽ làm TÁCH hết entity đang dùng trong cùng giao dịch
    // (Payment/Course/User...) → các dòng sau đó đọc proxy lazy bị LazyInitializationException "no session" (bug thật 14/11/2026: duyệt hoàn
    // tiền báo 500 ở LedgerService.recordRefundReversal). Dòng enrollments đã lọc @SQLRestriction nên không nằm sẵn trong context để bị cũ.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value =
            "UPDATE enrollments SET revoked_at = :now, revoked_reason = :reason, revoked_payment_id = :paymentId "
                    + "WHERE user_id = :userId AND course_id = :courseId AND revoked_at IS NULL")
    int revoke(@org.springframework.data.repository.query.Param("userId") Long userId,
               @org.springframework.data.repository.query.Param("courseId") Long courseId,
               @org.springframework.data.repository.query.Param("paymentId") Long paymentId,
               @org.springframework.data.repository.query.Param("reason") String reason,
               @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /**
     * Mua lại sau khi đã bị thu hồi: KÍCH HOẠT LẠI đúng dòng cũ (UNIQUE user+course không cho chèn dòng
     * mới). Giữ nguyên progress_pct/completed_at (không cộng lại XP hoàn thành khóa). Trả 0 nếu không có
     * dòng nào đang bị thu hồi.
     */
    // KHÔNG clearAutomatically: câu lệnh native chỉ đụng bảng enrollments, còn clear() sẽ làm TÁCH hết entity đang dùng trong cùng giao dịch
    // (Payment/Course/User...) → các dòng sau đó đọc proxy lazy bị LazyInitializationException "no session" (bug thật 14/11/2026: duyệt hoàn
    // tiền báo 500 ở LedgerService.recordRefundReversal). Dòng enrollments đã lọc @SQLRestriction nên không nằm sẵn trong context để bị cũ.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value =
            "UPDATE enrollments SET revoked_at = NULL, revoked_reason = NULL, revoked_payment_id = NULL, "
                    + "enrolled_at = :now WHERE user_id = :userId AND course_id = :courseId AND revoked_at IS NOT NULL")
    int reactivate(@org.springframework.data.repository.query.Param("userId") Long userId,
                   @org.springframework.data.repository.query.Param("courseId") Long courseId,
                   @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
