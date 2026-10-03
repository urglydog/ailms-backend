package com.lms.enrollment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CourseReviewDto {

    public record CreateReq(
            @NotNull(message = "Số sao đánh giá không được để trống")
            @Min(value = 1, message = "Số sao đánh giá phải từ 1 đến 5")
            @Max(value = 5, message = "Số sao đánh giá phải từ 1 đến 5")
            Integer rating,

            String comment
    ) {}

    public record Res(
            Long id,
            Long courseId,
            String courseTitle,
            String userName,
            String userAvatarUrl,
            Integer rating,
            String comment,
            Boolean isHidden,
            String moderationReason,
            LocalDateTime createdAt,
            String moderationStatus
    ) {}

    /** Giảng viên report 1 review (03/10/2026, mở rộng). */
    public record ReportReq(String reason) {}
}
