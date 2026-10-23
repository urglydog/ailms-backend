package com.lms.payment.dto;

import com.lms.common.enums.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DisputeDto {

    public record FileReq(
            @NotBlank String paymentTxnRef,
            @NotBlank String reason,
            String description,
            String refundBankAccount
    ) {}

    public record Res(
            Long id,
            String paymentTxnRef,
            String courseTitle,
            BigDecimal amount,
            String reason,
            String description,
            DisputeStatus status,
            String filedByEmail,
            LocalDateTime createdAt
    ) {}

    public record AdminRes(
            Long id,
            String paymentTxnRef,
            String courseTitle,
            BigDecimal amount,
            String reason,
            String description,
            DisputeStatus status,
            String filedByEmail,
            String refundBankAccount,
            String adminNote,
            String resolvedByEmail,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt
    ) {}

    public record ResolveReq(
            @NotNull DisputeStatus status,
            @NotBlank String note
    ) {}
}
