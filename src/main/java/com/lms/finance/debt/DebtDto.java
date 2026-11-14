package com.lms.finance.debt;

import com.lms.common.enums.DebtPaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

/** DTO công nợ giảng viên (14/11/2026). */
public class DebtDto {

    public record ChargeRes(String note, long amount, LocalDateTime createdAt) {}

    public record RepaymentRes(
            Long id,
            long amount,
            DebtPaymentStatus status,
            String checkoutUrl,
            /** Nội dung mã QR (VietQR) để vẽ QR ngay trên trang; null nếu PayOS không trả. */
            String qrCode,
            LocalDateTime createdAt,
            LocalDateTime paidAt
    ) {}

    /**
     * {@code level}: 0 = không nợ; 1 = đang nợ (nhắc nhở); 2 = quá hạn trả → không xuất bản khóa mới/không đăng ký quảng bá;
     * 3 = quá hạn dài → khóa tạm ngừng bán mới. {@code paused}: đang khiếu nại → chế tài tạm dừng.
     */
    public record Res(
            long debtAmount,
            LocalDateTime debtSince,
            long daysInDebt,
            int level,
            int restrictAfterDays,
            int suspendAfterDays,
            boolean paused,
            long minRepay,
            List<ChargeRes> recentCharges,
            List<RepaymentRes> pendingRepayments
    ) {}

    public record RepayReq(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) Long amount) {}

    public record AdminDebtRes(Long instructorId, String name, String email, long debtAmount, LocalDateTime debtSince, long daysInDebt, int level) {}

    public record WriteOffReq(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String reason) {}
}
