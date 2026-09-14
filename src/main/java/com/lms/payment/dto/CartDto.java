package com.lms.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO cho {@code /api/v1/cart/**} — giỏ hàng (06/09/2026, mở rộng ngoài đặc tả gốc). */
public class CartDto {

    /** 1 dòng trong giỏ hàng — đủ dữ liệu để hiển thị trực tiếp, không cần gọi thêm API
     * chi tiết khóa học. */
    public record ItemRes(
            Long courseId,
            String courseTitle,
            String courseSlug,
            String thumbnailUrl,
            String instructorName,
            BigDecimal price,
            LocalDateTime addedAt,
            /** (14/09/2026, mở rộng) — hiển thị đủ thông tin trên mỗi dòng giỏ hàng kiểu Udemy, không cần gọi thêm API chi tiết khóa. */
            BigDecimal avgRating,
            Long reviewCount,
            Integer totalDurationSec,
            Integer totalLessons,
            String level
    ) {}

    public record AddReq(@NotNull Long courseId) {}
}
