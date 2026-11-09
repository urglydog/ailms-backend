package com.lms.payment.dto;

import com.lms.common.enums.DisputeStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DisputeDto {

    public record FileReq(
            @NotBlank String paymentTxnRef,
            @NotBlank String reason,
            String description,
            String refundBankAccount,
            @AssertTrue(message = "Bạn phải đồng ý với chính sách hoàn tiền trước khi gửi khiếu nại")
            boolean termsAccepted
    ) {}

    public record AttachmentRes(Long id, String fileUrl, String fileType) {}

    public record Res(
            Long id,
            String paymentTxnRef,
            String courseTitle,
            BigDecimal amount,
            String reason,
            String description,
            DisputeStatus status,
            String filedByEmail,
            LocalDateTime createdAt,
            List<AttachmentRes> attachments,
            LocalDateTime resolvedAt,
            LocalDateTime reopenedAt
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
            LocalDateTime createdAt,
            List<AttachmentRes> attachments,
            String reopenReason,
            LocalDateTime reopenedAt,
            String previousAdminNote
    ) {}

    public record ResolveReq(
            @NotNull DisputeStatus status,
            @NotBlank String note
    ) {}

    /** Reopen (26/10/2026) — user báo "chưa nhận được tiền" sau khi đã RESOLVED_REFUNDED. */
    public record ReopenReq(
            @NotBlank String reopenReason
    ) {}
}
