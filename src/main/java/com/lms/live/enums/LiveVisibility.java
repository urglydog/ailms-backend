package com.lms.live.enums;

/**
 * Phạm vi hiển thị của phiên Live (BR-LIVE-01).
 *
 * <p>{@code COURSE_ONLY} — chỉ học viên đã ghi danh khóa học mới xem được.
 * {@code PUBLIC} — mọi tài khoản, kể cả Guest chưa đăng nhập, xem được.
 */
public enum LiveVisibility {
    COURSE_ONLY,
    PUBLIC
}
