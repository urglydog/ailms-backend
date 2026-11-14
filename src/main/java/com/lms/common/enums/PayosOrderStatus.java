package com.lms.common.enums;

/**
 * Trạng thái lệnh chi PayOS. {@code UNKNOWN} = gửi đi nhưng không biết kết quả (hết thời gian/lỗi mạng): có thể gửi lại với CÙNG
 * khóa idempotency, PayOS sẽ trả lại đúng lệnh cũ thay vì chi lần 2.
 */
public enum PayosOrderStatus {
    PENDING,
    SUBMITTED,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
