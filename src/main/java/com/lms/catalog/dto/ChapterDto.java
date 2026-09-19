package com.lms.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ChapterDto {

    public record CreateReq(
            @NotBlank(message = "Tiêu đề chương không được để trống")
            String title
    ) {}

    public record UpdateReq(
            @NotBlank(message = "Tiêu đề chương không được để trống")
            String title,
            /** Nullable — Phần không bắt buộc phải có mô tả. */
            String description
    ) {}

    public record ReorderReq(
            @NotEmpty(message = "Danh sách thứ tự không được để trống")
            List<Long> orderedIds
    ) {}

    public record Res(
            Long id,
            String title,
            Integer displayOrder,
            List<LessonDto.Res> lessons,
            String description
    ) {}
}
