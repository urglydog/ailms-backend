package com.lms.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO cho duyệt khóa học công khai (UC09, UC10) — chỉ dữ liệu marketing/mô tả, không có
 * field tác nghiệp phía Giảng viên (status Lesson, videoUrl...). Độc lập với
 * {@code CourseDto}/{@code ChapterDto}/{@code LessonDto} của F2.1 vì phục vụ Guest/Student,
 * không cần đăng nhập.
 */
public class CoursePublicDto {

    public record SummaryRes(
            Long id,
            String title,
            String slug,
            String instructorName,
            String thumbnailUrl,
            String level,
            BigDecimal price,
            Boolean isFree,
            BigDecimal avgRating,
            Long reviewCount,
            Integer totalLessons,
            /** UC09 mở rộng (14/09/2026) — tổng giây video, dùng cho bộ lọc "Video Duration" kiểu Udemy. */
            Integer totalDurationSec,
            String categorySlug,
            String categoryName,
            /** UC57 mở rộng (15/09/2026) — giá sau khi áp coupon autoApply tốt nhất (BR-COUPON-04); bằng {@code price} nếu không có coupon nào áp dụng được. */
            BigDecimal finalPrice,
            /** Null nếu không có coupon nào áp dụng — FE dùng để quyết định có hiện giá gạch ngang hay không. */
            Integer discountPercent
    ) {}

    public record DetailRes(
            Long id,
            String title,
            String slug,
            String description,
            String instructorName,
            String thumbnailUrl,
            String level,
            BigDecimal price,
            Boolean isFree,
            BigDecimal avgRating,
            Long reviewCount,
            Integer totalDurationSec,
            String categorySlug,
            String categoryName,
            List<ChapterRes> chapters,
            /** UC10 mở rộng (14/09/2026) — vùng "hero" nền đen kiểu Udemy ở trang chi tiết khóa. */
            LocalDateTime updatedAt,
            /** Nhãn hiển thị (vd "Tiếng Anh"), sinh từ mã ngôn ngữ qua {@code Locale}, không hardcode danh sách. Null nếu chưa bài nào có transcript. */
            String sourceLanguage,
            /** Ngôn ngữ đã lồng tiếng XONG (ít nhất 1 bài, {@code AudioTrack.status = COMPLETED}) — rỗng nếu chưa có. */
            List<String> dubbedLanguages,
            Long learnerCount,
            /** UC57 mở rộng (15/09/2026) — xem {@code SummaryRes.finalPrice}. */
            BigDecimal finalPrice,
            Integer discountPercent
    ) {}

    public record ChapterRes(
            Long id,
            String title,
            Integer displayOrder,
            List<LessonRes> lessons
    ) {}

    public record LessonRes(
            Long id,
            String title,
            Integer displayOrder,
            Boolean isPreview,
            Integer durationSec
    ) {}

    /** UC11 — dữ liệu phát video cho bài học Preview (Guest/Student chưa sở hữu khóa học). */
    public record PlayerRes(
            Long lessonId,
            String lessonTitle,
            Long courseId,
            String courseTitle,
            String courseSlug,
            String videoSource,
            String videoUrl,
            String youtubeId,
            Integer durationSec
    ) {}
}
