package com.lms.common.enums;

/**
 * Phạm vi áp dụng của {@code Coupon} (15/09/2026, mở rộng ngoài đặc tả gốc).
 *
 * <p>{@code SPECIFIC_COURSES}/{@code SINGLE_COURSE} đều dùng chung bảng nối
 * {@code CouponCourse} — khác biệt thuần túy về Ý NGHĨA HIỂN THỊ (chọn nhiều khóa / đúng 1
 * khóa), không khác nhau về cấu trúc dữ liệu (đơn giản hoá theo đúng gợi ý ở mục 5.3 tài liệu
 * đặc tả — không cần thêm cột FK riêng cho trường hợp 1 khóa).
 */
public enum CouponScopeType {
    /** Toàn hệ thống nếu người tạo là Admin; toàn bộ khóa CỦA CHÍNH người tạo nếu là Instructor (BR-COUPON-02). */
    ALL_COURSES,
    /** Danh sách khóa học cụ thể do người tạo chỉ định, qua {@code CouponCourse}. */
    SPECIFIC_COURSES,
    /** Đúng 1 khóa học, cũng lưu qua {@code CouponCourse} (chỉ có đúng 1 dòng). */
    SINGLE_COURSE
}
