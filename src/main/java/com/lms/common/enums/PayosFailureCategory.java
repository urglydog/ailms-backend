package com.lms.common.enums;

/**
 * Nhóm nguyên nhân khi một lệnh chi PayOS thất bại (16/11/2026): {@code TRANSIENT} = sự cố tạm thời phía hệ thống/ngân hàng (thử lại sau);
 * {@code ACCOUNT} = thông tin tài khoản nhận có vấn đề (thử lại cùng thông tin sẽ không bao giờ thành công); {@code UNKNOWN} = chưa phân loại
 * được (chỉ báo admin xem xét, không tự quyết định gì thay người dùng).
 */
public enum PayosFailureCategory {
    TRANSIENT,
    ACCOUNT,
    UNKNOWN
}
