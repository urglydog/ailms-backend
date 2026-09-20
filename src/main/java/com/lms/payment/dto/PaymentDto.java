package com.lms.payment.dto;

import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RevenueSource;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PaymentDto {

    public record CreateReq(
            @NotNull Long courseId,
            @NotNull String paymentMethod, // VNPAY, MOMO
            String billingName,
            String billingPhone,
            /** UC57 mở rộng (15/09/2026) — mã giảm giá tự nhập, optional. */
            String couponCode,
            /** "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — chỉ cần khi khóa học ở chế
             * độ PRIVATE_PASSWORD, bỏ qua với mọi khóa khác. */
            String courseAccessPassword,
            /** Chia doanh thu 2 mức (20/09/2026) — mã từ liên kết giới thiệu riêng của Giảng
             * viên (query {@code ?ref=}), optional. Khớp {@code Course.referralCode} thì tính
             * {@code RevenueSource.INSTRUCTOR_REFERRAL}, không khớp/không gửi thì
             * {@code ORGANIC} — xem {@code PaymentService.resolveRevenueSource}. */
            String referralCode
    ) {}

    /** Giỏ hàng (06/09/2026, mở rộng ngoài đặc tả gốc) — gộp thanh toán nhiều khóa học học
     * viên tự chọn trong 1 lần "Proceed to Checkout" (xem {@code PaymentService.
     * createBatchPayment}). */
    public record CreateBatchReq(
            @NotEmpty List<Long> courseIds,
            @NotNull String paymentMethod,
            String billingName,
            String billingPhone,
            /** UC57 mở rộng (15/09/2026) — CÙNG 1 mã áp cho mọi khóa trong giỏ, mỗi khóa tự
             * kiểm tra coupon tốt nhất RIÊNG (BR-COUPON-05) — khóa nào mã không hợp lệ vẫn
             * tính giá gốc (hoặc giá coupon autoApply nếu có), không ảnh hưởng khóa khác. */
            String couponCode,
            /** Chia doanh thu 2 mức (20/09/2026) — mã giới thiệu RIÊNG cho từng khóa trong
             * giỏ, khoá theo {@code courseId} (cùng tinh thần {@code couponCode}: mỗi khóa tự
             * kiểm tra khớp {@code Course.referralCode} của CHÍNH nó, không dùng chéo được).
             * NULL hoặc thiếu entry cho 1 khóa → khóa đó tính {@code ORGANIC}. */
            Map<Long, String> referralCodes
    ) {}

    public record PaymentUrlRes(
            String paymentUrl
    ) {}

    public record Res(
            String txnRef,
            BigDecimal amount,
            String paymentMethod,
            PaymentStatus status,
            LocalDateTime paidAt,
            String courseTitle,
            String gatewayTxnNo,
            String billingName,
            String billingPhone,
            /** UC57 mở rộng (15/09/2026) — NULL nếu không dùng coupon. */
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            String couponCode
    ) {}

    public record AdminRes(
            String txnRef,
            BigDecimal amount,
            BigDecimal platformFee,
            BigDecimal instructorEarning,
            String paymentMethod,
            PaymentStatus status,
            LocalDateTime paidAt,
            String courseTitle,
            String gatewayTxnNo,
            String userEmail,
            String billingName,
            String billingPhone,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            String couponCode,
            /** Chia doanh thu 2 mức (20/09/2026) — tỷ lệ ĐÃ áp dụng cho platformFee/instructorEarning ở trên. */
            RevenueSource revenueSource
    ) {}
}
