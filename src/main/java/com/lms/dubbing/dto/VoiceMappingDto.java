package com.lms.dubbing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VoiceMappingDto {

    /** UC47 — Admin thêm 1 giọng đọc Edge-TTS cho 1 ngôn ngữ. */
    public record CreateReq(
            @NotBlank(message = "Ngôn ngữ không được để trống")
            String language,

            @NotBlank(message = "Tên giọng đọc không được để trống")
            String voiceName,

            @NotBlank(message = "Giới tính giọng đọc không được để trống")
            String gender,

            @NotNull(message = "isDefault không được để trống")
            Boolean isDefault
    ) {}

    /** language/voiceName là khóa định danh — không đổi sau khi tạo, chỉ sửa các thuộc tính vận hành. */
    public record UpdateReq(
            @NotBlank(message = "Giới tính giọng đọc không được để trống")
            String gender,

            @NotNull(message = "isDefault không được để trống")
            Boolean isDefault,

            @NotNull(message = "isActive không được để trống")
            Boolean isActive
    ) {}

    public record Res(
            Long id,
            String language,
            String voiceName,
            String gender,
            Boolean isDefault,
            Boolean isActive
    ) {}
}
