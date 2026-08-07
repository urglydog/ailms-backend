package com.lms.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class LessonDto {

    /** F2.1 chỉ tạo metadata — video được nạp sau ở Giai đoạn 4 (UC34). */
    public record CreateReq(
            @NotBlank(message = "Tiêu đề bài học không được để trống")
            String title
    ) {}

    public record UpdateReq(
            @NotBlank(message = "Tiêu đề bài học không được để trống")
            String title,

            @NotNull(message = "Trạng thái Preview không được để trống")
            Boolean isPreview
    ) {}

    public record ReorderReq(
            @NotEmpty(message = "Danh sách thứ tự không được để trống")
            List<Long> orderedIds
    ) {}

    public record Res(
            Long id,
            String title,
            Integer displayOrder,
            Boolean isPreview,
            String status,
            String videoSource,
            String videoUrl
    ) {}
}
