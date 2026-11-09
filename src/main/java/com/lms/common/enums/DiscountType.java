package com.lms.common.enums;

/** Kiểu giảm giá của {@code Coupon} (15/09/2026, mở rộng ngoài đặc tả gốc). */
public enum DiscountType {
    /** Giảm theo % giá gốc (vd 20% off). */
    PERCENTAGE,
    /** Giảm số tiền cố định (vd giảm 50.000đ), không vượt quá giá gốc. */
    FIXED_AMOUNT
}
