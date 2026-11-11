package com.lms.finance.dto;

import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import java.math.BigDecimal;

/** DTO trang "Chia sẻ & Quảng bá" của giảng viên (11/11/2026, Giai đoạn D). */
public class ShareDto {

    /**
     * 1 khóa của giảng viên + mã giới thiệu riêng + thống kê đơn mua theo nguồn trong khoảng thời gian đã chọn.
     * {@code referralCode} chỉ trả về cho CHÍNH chủ khóa (endpoint chỉ cho giảng viên, chỉ liệt kê khóa của họ).
     */
    public record CourseShareRes(
            Long courseId,
            String title,
            String slug,
            String thumbnailUrl,
            CourseStatus status,
            CourseVisibility visibility,
            String referralCode,
            /** Chỉ khóa đã xuất bản mới có link chia sẻ dùng được. */
            boolean shareable,
            long referralOrders,
            BigDecimal referralEarning,
            long organicOrders,
            BigDecimal organicEarning
    ) {}
}
