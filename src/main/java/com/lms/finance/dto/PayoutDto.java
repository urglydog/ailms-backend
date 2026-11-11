package com.lms.finance.dto;

import com.lms.common.enums.LedgerEntryType;
import com.lms.common.enums.NameMatchStatus;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.PayoutStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO tài khoản nhận tiền / rút tiền (11/11/2026, Giai đoạn C). Không DTO nào của giảng viên chứa số tài khoản đầy đủ. */
public class PayoutDto {

    public record BankRes(String code, String name) {}

    public record LinkAccountReq(
            @NotBlank String bankCode,
            @NotBlank @Pattern(regexp = "^[0-9 ]{6,25}$", message = "Số tài khoản chỉ gồm chữ số (6–20 số)") String accountNumber,
            @NotBlank @Size(max = 100) String holderName
    ) {}

    public record AccountRes(
            Long id,
            String bankCode,
            String bankName,
            String accountMask,
            String holderName,
            PayoutAccountStatus status,
            NameMatchStatus nameMatchStatus,
            LocalDateTime usableFrom,
            LocalDateTime confirmedAt
    ) {}

    public record SummaryRes(
            BigDecimal available,
            BigDecimal pending,
            BigDecimal frozen,
            BigDecimal withdrawable,
            BigDecimal paidOut,
            BigDecimal minPayoutAmount,
            LocalDate nextPayoutDate,
            AccountRes account,
            boolean cryptoConfigured,
            boolean hasOpenPayout
    ) {}

    public record LedgerEntryRes(
            Long id,
            LedgerEntryType type,
            BigDecimal amount,
            LocalDateTime availableAt,
            boolean matured,
            String note,
            LocalDateTime createdAt
    ) {}

    public record RequestPayoutReq(@NotNull @DecimalMin("1") BigDecimal amount) {}

    public record ConfirmReq(@NotBlank @Size(max = 100) String token) {}

    /** {@code type} = ACCOUNT hoặc PAYOUT, {@code id} = id tài khoản/yêu cầu rút vừa xác nhận. */
    public record ConfirmRes(String type, Long id) {}

    public record PayoutRes(
            Long id,
            String code,
            BigDecimal amount,
            PayoutStatus status,
            String bankName,
            String accountMask,
            String holderName,
            LocalDate scheduledFor,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            String transferRef,
            String rejectReason
    ) {}

    public record AdminPayoutRes(
            Long id,
            String code,
            BigDecimal amount,
            PayoutStatus status,
            Long instructorId,
            String instructorName,
            String instructorEmail,
            String bankCode,
            String bankName,
            String accountMask,
            String holderName,
            /** Tên chủ tài khoản có khớp tên giảng viên đã xác minh không — lệch thì admin phải kiểm tra kỹ trước khi chi. */
            boolean nameMatches,
            LocalDate scheduledFor,
            LocalDateTime createdAt,
            LocalDateTime approvedAt,
            LocalDateTime paidAt,
            String transferRef,
            String proofUrl,
            String rejectReason
    ) {}

    /** Số tài khoản đầy đủ — chỉ admin xem, mỗi lần xem đều ghi nhật ký. */
    public record AccountRevealRes(Long payoutId, String bankName, String accountNumber, String holderName) {}

    public record MarkPaidReq(
            @NotBlank @Size(max = 100) String transferRef,
            @Size(max = 500) String proofUrl
    ) {}

    public record ReasonReq(@NotBlank @Size(max = 500) String reason) {}
}
