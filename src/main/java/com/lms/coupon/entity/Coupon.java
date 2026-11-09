package com.lms.coupon.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.CouponScopeType;
import com.lms.common.enums.DiscountType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Mã giảm giá (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57).
 * Xem doc/01_ThietKeLai_GiangVien_ThanhToan_Coupon.md mục 3.3/3.4.
 *
 * <p><b>BR-COUPON-01 (không cộng dồn):</b> mỗi khóa học trong giỏ hàng chỉ áp dụng ĐÚNG 1
 * coupon — {@code CouponService.resolveBestPrice} chọn coupon cho mức giá thấp nhất, không
 * cộng dồn nhiều lớp giảm giá.
 *
 * <p><b>BR-COUPON-02/03 (phạm vi Giảng viên):</b> Instructor chỉ tạo được coupon cho khóa học
 * CỦA CHÍNH MÌNH — {@code scopeType=ALL_COURSES} do Instructor tạo được hiểu là "toàn bộ khóa
 * của người đó", không phải toàn hệ thống (khác Admin tạo cùng scope).
 *
 * <p><b>BR-COUPON-04 (hiển thị):</b> {@code autoApply=true} hiển thị giá đã giảm ngay trên thẻ
 * khóa học, không cần nhập mã; {@code code} chỉ có ý nghĩa khi {@code autoApply=false}.
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
public class Coupon extends BaseEntity {

    /** NULL khi {@code autoApply=true} (không cần mã, tự hiển thị giá đã giảm). */
    @Column(name = "code", unique = true, length = 50)
    private String code;

    @Column(name = "auto_apply", nullable = false)
    private Boolean autoApply = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** PERCENTAGE: 1-100. FIXED_AMOUNT: số tiền VNĐ > 0. */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private CouponScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** NULL = không giới hạn tổng số lượt dùng. */
    @Column(name = "max_usage_count")
    private Integer maxUsageCount;

    /** NULL = không giới hạn số lượt dùng/học viên. */
    @Column(name = "max_usage_per_user")
    private Integer maxUsagePerUser;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
