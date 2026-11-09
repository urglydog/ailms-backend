package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Danh sách email được Giảng viên mời cho khóa học {@code visibility == PRIVATE_INVITE}
 * (19/09/2026) — lưu theo EMAIL chứ không phải {@code User} vì người được mời có thể chưa từng
 * có tài khoản lúc được mời (khác {@code Enrollment}/{@code CourseReview} luôn gắn User đã tồn
 * tại).
 */
@Entity
@Table(name = "course_invites",
        uniqueConstraints = @UniqueConstraint(name = "uk_course_invites_course_email", columnNames = {"course_id", "email"}))
@Getter
@Setter
public class CourseInvite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "email", nullable = false, length = 100)
    private String email;
}
