package com.lms.wishlist.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Danh sách yêu thích (14/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả
 * gốc của đồ án, cùng tinh thần {@link com.lms.payment.entity.CartItem} (giỏ hàng). Học viên
 * lưu lại khóa học CHƯA SỞ HỮU để theo dõi, không nhất thiết đã có trong giỏ hàng — 2 danh
 * sách độc lập, không loại trừ nhau.
 *
 * <p>Chỗ dành cho tương lai: khi khóa học có logic giảm giá, hệ thống sẽ gửi email cho mọi
 * học viên có khóa đó trong wishlist (chưa làm ở giai đoạn này — chưa có logic giảm giá).
 */
@Entity
@Table(name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_items_user_course", columnNames = {"user_id", "course_id"}))
@Getter
@Setter
public class WishlistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
