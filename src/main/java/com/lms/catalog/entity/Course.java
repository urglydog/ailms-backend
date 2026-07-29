package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.common.enums.CourseStatus;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Khóa học (UC31, UC36, UC42).
 *
 * <p>Vòng đời: {@code DRAFT -> PENDING -> PUBLISHED | REJECTED -> ARCHIVED}.
 * Chỉ khóa {@code PUBLISHED} mới nhận ghi danh (BR-ENROLL-01).
 * Khóa đã có học viên chỉ được xoá mềm sang {@code ARCHIVED} (BR-COURSE-03).
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
public class Course extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 280)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Bắt buộc có trước khi gửi kiểm duyệt (BR-COURSE-01). */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** BEGINNER / INTERMEDIATE / ADVANCED. */
    @Column(name = "level", nullable = false, length = 20)
    private String level = "BEGINNER";

    /** price = 0 nghĩa là miễn phí (BR-PAY-01). */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * Cột dẫn xuất từ {@code price == 0} nhưng vẫn lưu tường minh để truy vấn
     * lọc "miễn phí" nhanh (UC09). PHẢI cập nhật đồng thời với {@code price}.
     */
    @Column(name = "is_free", nullable = false)
    private Boolean isFree = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CourseStatus status = CourseStatus.DRAFT;

    /** Bắt buộc >= 20 ký tự khi status = REJECTED (BR-COURSE-04). */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /** Số lần đã gửi đi kiểm duyệt — tối đa 5 (BR-COURSE-04). */
    @Column(name = "resubmit_count", nullable = false)
    private Integer resubmitCount = 0;

    /** Tính lại mỗi khi có CourseReview mới. */
    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "total_lessons", nullable = false)
    private Integer totalLessons = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Giảng viên sở hữu — mọi thao tác sửa/xoá phải kiểm quyền này (BR-ROLE-01). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;
}
