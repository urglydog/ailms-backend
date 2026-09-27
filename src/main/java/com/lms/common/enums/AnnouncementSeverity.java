package com.lms.common.enums;

/**
 * Mức độ thông báo hệ thống từ Admin. HIGH hiển thị thành banner cố định đầu trang (vd "Bảo trì
 * hệ thống") — thay vì chỉ nằm trong chuông thông báo như MEDIUM/LOW.
 *
 * <p>Dùng bởi: SystemAnnouncement</p>
 */
public enum AnnouncementSeverity {
    HIGH,
    MEDIUM,
    LOW
}
