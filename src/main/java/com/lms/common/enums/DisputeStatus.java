package com.lms.common.enums;

/**
 * Trạng thái khiếu nại giao dịch thanh toán.
 * OPEN -> RESOLVED_REFUNDED (Admin xác nhận hoàn tiền thủ công) hoặc RESOLVED_REJECTED.
 * RESOLVED_REFUNDED -> REOPENED (user báo chưa nhận được tiền, trong hạn, 1 lần) -> resolve lại
 * thành RESOLVED_REFUNDED hoặc RESOLVED_REJECTED (26/10/2026).
 *
 * <p>Dùng bởi: PaymentDispute</p>
 */
public enum DisputeStatus {
    OPEN,
    RESOLVED_REFUNDED,
    RESOLVED_REJECTED,
    /** User báo "chưa nhận được tiền" sau khi đã RESOLVED_REFUNDED — chỉ mở lại được 1 lần,
     * trong vòng N ngày kể từ resolvedAt (xem DisputeService#reopen). */
    REOPENED
}
