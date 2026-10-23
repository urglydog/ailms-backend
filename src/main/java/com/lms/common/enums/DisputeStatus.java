package com.lms.common.enums;

/**
 * Trạng thái khiếu nại giao dịch thanh toán.
 * OPEN -> RESOLVED_REFUNDED (Admin xác nhận hoàn tiền thủ công) hoặc RESOLVED_REJECTED.
 *
 * <p>Dùng bởi: PaymentDispute</p>
 */
public enum DisputeStatus {
    OPEN,
    RESOLVED_REFUNDED,
    RESOLVED_REJECTED
}
