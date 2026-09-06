package com.lms.payment.dto;

import com.lms.common.enums.PaymentStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PaymentDto {

    public record CreateReq(
            @NotNull Long courseId,
            @NotNull String paymentMethod, // VNPAY, MOMO
            String billingName,
            String billingPhone
    ) {}

    /** Giỏ hàng (06/09/2026, mở rộng ngoài đặc tả gốc) — gộp thanh toán nhiều khóa học học
     * viên tự chọn trong 1 lần "Proceed to Checkout" (xem {@code PaymentService.
     * createBatchPayment}). */
    public record CreateBatchReq(
            @NotEmpty List<Long> courseIds,
            @NotNull String paymentMethod,
            String billingName,
            String billingPhone
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
            String billingPhone
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
            String billingPhone
    ) {}
}
