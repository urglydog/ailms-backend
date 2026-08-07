package com.lms.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public class CategoryDto {

    public record CreateReq(
            @NotBlank(message = "Tên danh mục không được để trống")
            String name
    ) {}

    public record UpdateReq(
            @NotBlank(message = "Tên danh mục không được để trống")
            String name
    ) {}

    public record Res(
            Long id,
            String name,
            String slug
    ) {}
}
