package com.lms.enrollment.dto;

import java.math.BigDecimal;

public class EnrollmentDto {

    public record Res(
            Long courseId,
            String courseTitle,
            String courseSlug,
            String thumbnailUrl,
            String categoryName,
            Boolean isFree,
            BigDecimal price,
            /** Đã đánh giá khóa này chưa — để FE biết hiện "Viết đánh giá" hay "Đã đánh giá". */
            Boolean alreadyReviewed
    ) {}
}
