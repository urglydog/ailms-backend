package com.lms.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO cho {@code /api/v1/wishlist/**} — danh sách yêu thích (14/09/2026, mở rộng ngoài đặc tả gốc). */
public class WishlistDto {

    /** 1 dòng trong wishlist — đủ dữ liệu để hiển thị trực tiếp, không cần gọi thêm API chi tiết khóa học. */
    public record ItemRes(
            Long courseId,
            String courseTitle,
            String courseSlug,
            String thumbnailUrl,
            String instructorName,
            BigDecimal price,
            Boolean isFree,
            /** Cache sẵn trên {@code Course} (không tính lại mỗi lần) — hiển thị sao trên thẻ card kiểu Udemy. */
            BigDecimal avgRating,
            Long reviewCount,
            LocalDateTime addedAt
    ) {}

    public record AddReq(@NotNull Long courseId) {}
}
