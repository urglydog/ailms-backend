package com.lms.promotion.dto;

import com.lms.common.enums.PromotionPlacement;
import com.lms.common.enums.PromotionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO chương trình quảng bá (Giai đoạn E, 11/11/2026). */
public class PromotionDto {

    public record CreateReq(
            @NotNull Long courseId,
            @NotNull PromotionPlacement placement,
            @Size(max = 500) String note
    ) {}

    /** {@code startAt} trống = bắt đầu ngay. {@code endAt} phải sau {@code startAt} và không quá 90 ngày. */
    public record ApproveReq(
            LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            @Min(0) @Max(100) Integer priority,
            @Size(max = 500) String note
    ) {}

    public record ReasonReq(@NotBlank @Size(max = 500) String reason) {}

    /**
     * Hiệu quả ước tính: đơn/doanh thu của khóa trong thời gian chạy so với khoảng bằng nhau ngay trước đó. KHÔNG phải đo
     * quy kết chính xác (chưa đo lượt xem/click) — chỉ để tham khảo.
     */
    public record Res(
            Long id,
            Long courseId,
            String courseTitle,
            String courseSlug,
            PromotionPlacement placement,
            PromotionStatus status,
            String requestNote,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer priority,
            String adminNote,
            LocalDateTime createdAt,
            /** Đang hiển thị thật cho học viên ngay lúc này. */
            boolean active,
            long ordersDuring,
            BigDecimal revenueDuring,
            long ordersBefore,
            BigDecimal revenueBefore,
            String instructorName,
            String instructorEmail
    ) {}

    /**
     * Kết quả kệ nổi bật. Record riêng (giống {@code SearchSliceRes}) vì kết quả được cache trong Redis bằng Jackson.
     * Chỉ chứa tóm tắt khóa học công khai — không lộ gì về chương trình quảng bá hay giảng viên đăng ký.
     */
    public record FeaturedRes(java.util.List<com.lms.catalog.dto.CoursePublicDto.SummaryRes> content) {}
}
