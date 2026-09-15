package com.lms.coupon.dto;

import com.lms.common.enums.CouponScopeType;
import com.lms.common.enums.DiscountType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho {@code /api/v1/coupons/**} (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57). */
public class CouponDto {

    /**
     * {@code code} bắt buộc khi {@code autoApply=false}, BỎ QUA (luôn lưu NULL) khi
     * {@code autoApply=true} — coupon tự động không cần mã (BR-COUPON-04).
     * {@code courseIds} bắt buộc khi {@code scopeType != ALL_COURSES}.
     */
    public record CreateReq(
            String code,
            @NotNull Boolean autoApply,
            @NotNull DiscountType discountType,
            @NotNull BigDecimal discountValue,
            @NotNull CouponScopeType scopeType,
            List<Long> courseIds,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            Integer maxUsageCount,
            Integer maxUsagePerUser
    ) {}

    public record UpdateReq(
            String code,
            @NotNull Boolean autoApply,
            @NotNull DiscountType discountType,
            @NotNull BigDecimal discountValue,
            @NotNull CouponScopeType scopeType,
            List<Long> courseIds,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            Integer maxUsageCount,
            Integer maxUsagePerUser,
            @NotNull Boolean isActive
    ) {}

    public record CourseRef(Long courseId, String courseTitle) {}

    public record Res(
            Long id,
            String code,
            Boolean autoApply,
            DiscountType discountType,
            BigDecimal discountValue,
            CouponScopeType scopeType,
            List<CourseRef> courses,
            String createdByName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer maxUsageCount,
            Integer maxUsagePerUser,
            Boolean isActive,
            long usageCount
    ) {}

    /**
     * Giá đã tính coupon cho 1 khóa học — dùng cả cho hiển thị thẻ khóa học (chỉ xét coupon
     * {@code autoApply}) lẫn xem trước lúc thanh toán (có thêm mã học viên tự nhập).
     * {@code enteredCodeValid} chỉ có ý nghĩa khi có truyền mã — luôn {@code true} nếu không
     * nhập mã nào (không có gì để báo lỗi).
     */
    /** Xem trước giá sau khi nhập mã ở giỏ hàng/thanh toán — không tạo giao dịch nào. */
    public record PreviewReq(@NotNull Long courseId, String code) {}

    public record PriceRes(
            BigDecimal originalPrice,
            BigDecimal finalPrice,
            Integer discountPercent,
            String appliedCouponCode,
            boolean enteredCodeValid
    ) {}
}
