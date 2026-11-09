package com.lms.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MaterialFolderReq(
        @NotBlank(message = "Tên thư mục không được để trống")
        String name,
        
        @NotNull(message = "ID khóa học là bắt buộc")
        Long courseId,
        
        Long parentId
) {
}
