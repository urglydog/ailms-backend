package com.lms.common.enums;

/** Khiếu nại của giảng viên với quyết định "giảng viên chịu toàn bộ" (14/11/2026). */
public enum AppealStatus {
    NONE,
    OPEN,
    /** Admin giữ nguyên quyết định. */
    UPHELD,
    /** Admin giảm về chế độ Chuẩn và hoàn lại phần phạt vượt mức. */
    REDUCED
}
