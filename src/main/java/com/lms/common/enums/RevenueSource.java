package com.lms.common.enums;

/**
 * Nguồn dẫn đến 1 lượt mua khóa học (20/09/2026, tính năng mới — chia doanh thu 2 mức) —
 * quyết định tỷ lệ ăn chia áp dụng cho {@code Payment} tại thời điểm {@code PAID}, xem
 * {@code PaymentService.applyOutcome}.
 *
 * <p>{@code ORGANIC}: học viên tự tìm thấy khóa học qua tìm kiếm/duyệt danh mục trên nền
 * tảng — Giảng viên 37%, nền tảng 63%.
 *
 * <p>{@code INSTRUCTOR_REFERRAL}: học viên mua qua liên kết giới thiệu riêng của Giảng
 * viên ({@code Course.referralCode}, Giảng viên tự quảng bá ngoài nền tảng — Facebook,
 * YouTube...) — Giảng viên 97%, nền tảng 3%.
 *
 * <p>Dùng bởi: Payment</p>
 */
public enum RevenueSource {
    ORGANIC,
    INSTRUCTOR_REFERRAL
}
