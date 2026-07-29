package com.lms.common.enums;

/**
 * Trạng thái yêu cầu nâng cấp Giảng viên (BR-ROLE-04).
 * Mỗi học viên chỉ có tối đa 1 bản PENDING; bị REJECTED phải chờ >= 7 ngày.
 * LƯU Ý: enum này bị thiếu trong sơ đồ lớp của KLTN, cần bổ sung vào tài liệu.
 *
 * <p>Dùng bởi: InstructorRequest</p>
 */
public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
