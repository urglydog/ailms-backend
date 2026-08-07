package com.lms.catalog.dto;

import java.math.BigDecimal;
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
            String categorySlug,
            String categoryName
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
            String categorySlug,
            String categoryName,
            List<ChapterRes> chapters
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
}
