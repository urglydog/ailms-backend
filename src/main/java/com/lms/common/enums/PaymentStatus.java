package com.lms.common.enums;

/**
 * Trạng thái giao dịch (BR-PAY-03).
 * PENDING -> PAID khi nhận callback IPN hợp lệ (idempotent theo txnRef).
 * PENDING -> EXPIRED nếu không có callback sau 15 phút.
 *
 * <p>Dùng bởi: Payment</p>
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    EXPIRED
}
