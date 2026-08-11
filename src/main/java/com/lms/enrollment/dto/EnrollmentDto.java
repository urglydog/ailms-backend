package com.lms.enrollment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
            Boolean alreadyReviewed,
            /** UC22 — % bài COMPLETED / tổng bài READY (BR-PROGRESS-02). */
            BigDecimal progressPct,
            /** Chỉ có giá trị khi progressPct đạt 100 (một chiều). */
            LocalDateTime completedAt,
            /** BR-PROGRESS-04 — MAX điểm Quiz mọi bộ. Luôn null ở Giai đoạn 6, Quiz thật làm ở Giai đoạn 7. */
            Integer quizScore
    ) {}
}
