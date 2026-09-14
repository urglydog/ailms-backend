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
            Integer quizScore,
            /** "Học ngay" — bài học đầu tiên của khoá; null nếu khoá chưa có bài học nào. */
            Long firstLessonId,
            /** "Khóa học của tôi" (trang My Courses, giao diện kiểu Udemy) — tên giảng viên hiển thị trên thẻ card. */
            String instructorName,
            /** Số sao (1-5) học viên TỰ chấm cho khóa này; null nếu chưa đánh giá (khác {@code alreadyReviewed}: field này mang cả giá trị, không chỉ cờ boolean). */
            Integer myRating,
            /** Ngày ghi danh — dùng cho sort "Recently Enrolled". */
            LocalDateTime enrolledAt,
            /** Lần gần nhất học viên xem 1 bài bất kỳ trong khóa — dùng cho sort "Recently Accessed"; null nếu ghi danh xong chưa xem bài nào. */
            LocalDateTime lastAccessedAt
    ) {}
}
