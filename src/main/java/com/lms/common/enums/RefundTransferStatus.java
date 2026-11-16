package com.lms.common.enums;

/** Giai đoạn B Epic hoàn tiền (11/11/2026): RefundTransferStatus. */
public enum RefundTransferStatus {
    PENDING_TRANSFER,
    /** Chuyển khoản thất bại vì tài khoản nhận có vấn đề; chờ học viên cập nhật tài khoản (16/11/2026). Giữ ngắn gọn vì cột chỉ dài 20 ký tự. */
    AWAITING_ACCOUNT,
    TRANSFERRED
}
