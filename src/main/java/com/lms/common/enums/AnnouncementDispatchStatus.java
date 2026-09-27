package com.lms.common.enums;

/**
 * Trạng thái phát tán (fan-out) của 1 thông báo hệ thống — xử lý nền qua job Redis
 * (SystemAnnouncementService), không đồng bộ trong request tạo.
 * PENDING -> PROCESSING -> DONE | FAILED.
 *
 * <p>Dùng bởi: SystemAnnouncement</p>
 */
public enum AnnouncementDispatchStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED
}
