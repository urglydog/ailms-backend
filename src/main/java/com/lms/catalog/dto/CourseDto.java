package com.lms.catalog.dto;

import com.lms.common.enums.CourseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseDto {

    public record CreateReq(
            @NotBlank(message = "Tiêu đề khóa học không được để trống")
            String title,

            String description,

            @NotNull(message = "Danh mục không được để trống")
            Long categoryId,

            String level,

            @NotNull(message = "Giá không được để trống")
            @DecimalMin(value = "0", message = "Giá không được âm")
            BigDecimal price
    ) {}

    public record UpdateReq(
            @NotBlank(message = "Tiêu đề khóa học không được để trống")
            String title,

            String description,

            String thumbnailUrl,

            @NotNull(message = "Danh mục không được để trống")
            Long categoryId,

            String level,

            @NotNull(message = "Giá không được để trống")
            @DecimalMin(value = "0", message = "Giá không được âm")
            BigDecimal price
    ) {}

    public record RejectReq(
            @NotBlank(message = "Lý do từ chối không được để trống")
            @Size(min = 20, message = "Lý do từ chối phải có ít nhất 20 ký tự")
            String reason
    ) {}

    public record SummaryRes(
            Long id,
            String title,
            String slug,
            CourseStatus status,
            String thumbnailUrl,
            String categoryName,
            BigDecimal price,
            Boolean isFree,
            BigDecimal avgRating,
            Integer totalLessons,
            LocalDateTime createdAt
    ) {}

    public record DetailRes(
            Long id,
            String title,
            String slug,
            String description,
            String thumbnailUrl,
            String level,
            BigDecimal price,
            Boolean isFree,
            CourseStatus status,
            String rejectReason,
            Integer resubmitCount,
            Long categoryId,
            String categoryName,
            Long instructorId,
            String instructorName,
            List<ChapterDto.Res> chapters,
            List<String> missingConditions,
            boolean canSubmit
    ) {}
}
