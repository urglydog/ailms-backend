package com.lms.enrollment.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bản ghi sở hữu khóa học (UC12, UC14).
 *
 * <p>BR-ENROLL-01: chỉ tạo khi (a) khóa miễn phí và học viên bấm Đăng ký, hoặc
 * (b) {@code Payment.status = PAID}. Chỉ ghi danh được khóa {@code PUBLISHED}.
 *
 * <p><b>KHÔNG có thao tác xoá/hủy ghi danh</b> — sở hữu là vĩnh viễn, đây là thiết
 * kế có chủ đích. Đừng thêm endpoint {@code DELETE /enrollments/{id}}. Khóa học
 * chuyển {@code ARCHIVED} thì học viên đã sở hữu vẫn truy cập đầy đủ (BR-ENROLL-03).
 */
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_user_course", columnNames = {"user_id", "course_id"}))
@Getter
@Setter
public class Enrollment extends BaseEntity {

    /** Tỷ lệ bài học đã COMPLETED trên tổng số bài (BR-PROGRESS-02). */
    @Column(name = "progress_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPct = BigDecimal.ZERO;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    /** Chỉ set khi progressPct đạt 100. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
