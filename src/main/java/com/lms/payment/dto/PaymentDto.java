package com.lms.payment.dto;

import com.lms.common.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDto {

    public record CreateReq(
            @NotNull Long courseId,
            @NotNull String paymentMethod, // VNPAY, MOMO
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
