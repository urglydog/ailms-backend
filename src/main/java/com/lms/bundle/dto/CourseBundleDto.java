package com.lms.bundle.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseBundleDto {

    public record CreateReq(
            String title,
            String description,
            Integer discountPercent,
            List<Long> courseIds
    ) {}

    public record UpdateReq(
            String title,
            String description,
            Integer discountPercent,
            Boolean isActive,
            List<Long> courseIds
    ) {}

    public record Res(
            Long id,
            String title,
            String description,
            Integer discountPercent,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<CourseItemRes> courses,
            BigDecimal originalPrice,
            BigDecimal discountAmount,
            BigDecimal finalPrice
    ) {}

    public record CourseItemRes(
            Long id,
            String title,
            String thumbnail,
            BigDecimal price
    ) {}
}
