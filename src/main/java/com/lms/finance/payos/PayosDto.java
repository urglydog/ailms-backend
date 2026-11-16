package com.lms.finance.payos;

import com.lms.common.enums.PayosFailureCategory;
import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import java.time.LocalDateTime;

/** DTO chi tự động qua PayOS — không DTO nào chứa số tài khoản đầy đủ. */
public class PayosDto {

    public record StatusRes(boolean enabled, Long balance, String balanceError, long maxAmount) {}

    public record OrderRes(
            Long id,
            PayosOrderKind kind,
            Long entityId,
            int attempt,
            String referenceId,
            long amount,
            String accountLast4,
            PayosOrderStatus status,
            String payosState,
            /** Tên chủ tài khoản do ngân hàng trả về; so với {@code holderNameEntered} để phát hiện chi nhầm người. */
            String resolvedAccountName,
            String holderNameEntered,
            boolean nameMismatch,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            /** Nhóm nguyên nhân khi FAILED (tạm thời / tài khoản / chưa rõ); null nếu không thất bại. */
            PayosFailureCategory failureCategory,
            /** Chỉ có ở lệnh FAILED mới nhất: thời điểm sớm nhất được chi lại (null = không bị chờ). */
            LocalDateTime nextRetryAt,
            /** Chỉ có ở lệnh FAILED mới nhất: số lần chi lại còn lại. */
            Integer attemptsLeft,
            /** Chỉ có ở lệnh FAILED mới nhất: BE có cho chi lại ngay lúc này không. */
            Boolean canRetry,
            /** Lý do không cho chi lại (null nếu được phép hoặc không áp dụng). */
            String retryBlockedReason
    ) {}
}
