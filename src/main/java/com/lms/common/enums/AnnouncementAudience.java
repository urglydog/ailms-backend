package com.lms.common.enums;

/**
 * Phạm vi người nhận thông báo hệ thống từ Admin. {@code SPECIFIC_USER} bắt buộc đi kèm
 * {@code SystemAnnouncement.targetUserId}.
 *
 * <p>Dùng bởi: SystemAnnouncement</p>
 */
public enum AnnouncementAudience {
    ALL,
    INSTRUCTOR,
    STUDENT,
    SPECIFIC_USER
}
