package com.lms.payment.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc của
 * đồ án. 1 học viên có 1 giỏ hàng ẢO là tập hợp mọi {@code CartItem} của họ — không cần
 * entity "Cart" cha riêng, giống cách {@link com.lms.enrollment.entity.Enrollment} không
 * cần "EnrollmentBatch" cha.
 *
 * <p>Chỉ khóa học TRẢ PHÍ mới thêm được vào giỏ (xem {@code CartService.addItem}) — khóa
 * miễn phí ghi danh thẳng qua {@code EnrollmentService.enrollFreeCourse} (UC12), không đi
 * qua giỏ hàng.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_user_course", columnNames = {"user_id", "course_id"}))
@Getter
@Setter
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
