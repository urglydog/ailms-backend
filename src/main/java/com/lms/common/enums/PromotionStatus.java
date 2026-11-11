package com.lms.common.enums;

/** Trạng thái đăng ký quảng bá (Giai đoạn E, 11/11/2026). "Đang chạy" suy ra từ APPROVED + khung thời gian. */
public enum PromotionStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    ENDED
}
