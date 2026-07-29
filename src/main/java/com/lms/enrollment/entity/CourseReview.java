package com.lms.enrollment.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Đánh giá và bình luận khóa học (UC23), Admin ẩn nếu vi phạm (UC44).
 *
 * <p>Chỉ học viên <b>đã sở hữu</b> khóa học mới được đánh giá, và mỗi người chỉ
 * đánh giá một lần (UNIQUE user + course).
 */
@Entity
@Table(name = "course_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_user_course", columnNames = {"user_id", "course_id"}))
@Getter
@Setter
public class CourseReview extends BaseEntity {

    /** Thang 1-5. */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** Admin ẩn khi vi phạm tiêu chuẩn cộng đồng (UC44). */
    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
