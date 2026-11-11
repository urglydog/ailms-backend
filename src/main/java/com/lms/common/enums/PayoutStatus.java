package com.lms.common.enums;

/** Giai đoạn C Epic hoàn tiền – chi trả (11/11/2026): PayoutStatus. */
public enum PayoutStatus {
    PENDING_EMAIL,
    REQUESTED,
    APPROVED,
    PAID,
    REJECTED,
    CANCELLED,
    FAILED
}
