package com.lms.catalog.service;

import com.lms.catalog.dto.CoursePublicDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.CourseReviewRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duyệt khóa học công khai (UC09, UC10) — chỉ đọc, chỉ trả khóa {@code PUBLISHED} (BR-ROLE-03).
 * Không đụng {@code CourseService}/{@code CourseController} của F2.1 (chỉ ghi, chỉ đọc khóa của
 * chính Giảng viên/hàng đợi Admin).
 */
@Service
@RequiredArgsConstructor
public class CoursePublicService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final CourseReviewRepository courseReviewRepository;

    @Transactional(readOnly = true)
    public Page<SummaryRes> search(
            String keyword, String categorySlug, String level, String priceType, String sortBy, Pageable pageable) {
        Boolean isFree = switch (priceType == null ? "" : priceType) {
            case "free" -> Boolean.TRUE;
            case "paid" -> Boolean.FALSE;
            default -> null;
        };
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedLevel = (level == null || level.isBlank()) ? null : level.toUpperCase(Locale.ROOT);

        // `pageable` vẫn giữ nguyên sort mặc định (createdAt DESC, xem @PageableDefault ở
        // Controller) — đúng cho "Mới nhất". 3 lựa chọn còn lại (rating/reviews/relevance) không
        // ứng với 1 cột duy nhất (reviewCount không lưu sẵn, relevance cần so khớp từ khóa) nên
        // được sắp lại bằng Comparator ngay trên trang đã fetch. Chỉ đúng ở quy mô hiện tại (fetch
        // 1 trang lớn duy nhất, chưa có phân trang thật) — cần viết lại bằng subquery SQL nếu
        // catalog lớn tới mức cần phân trang thật.
        Page<Course> page = courseRepository.searchPublic(normalizedKeyword, categorySlug, normalizedLevel, isFree, pageable);
        List<SummaryRes> content = page.getContent().stream().map(this::mapToSummaryRes).collect(Collectors.toList());

        Comparator<SummaryRes> comparator = resolveComparator(sortBy, normalizedKeyword);
        if (comparator != null) {
            content.sort(comparator);
        }

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /** {@code null} = giữ nguyên thứ tự DB đã trả (mặc định "Mới nhất"). */
    private Comparator<SummaryRes> resolveComparator(String sortBy, String keyword) {
        return switch (sortBy == null ? "" : sortBy) {
            case "rating" -> Comparator.comparing(SummaryRes::avgRating).reversed();
            case "reviews" -> Comparator.comparing(SummaryRes::reviewCount).reversed();
            case "relevance" -> relevanceComparator(keyword);
            default -> null;
        };
    }

    /**
     * "Phù hợp nhất": chưa có hạ tầng tìm kiếm full-text (Elasticsearch, MySQL FULLTEXT) nên dùng
     * bản đơn giản — tiêu đề BẮT ĐẦU bằng từ khóa xếp trước tiêu đề chỉ CHỨA từ khóa ở giữa, cùng
     * hạng thì xếp alphabet. Không có từ khóa thì không có gì để so khớp — giữ nguyên thứ tự mặc
     * định (Mới nhất).
     */
    private Comparator<SummaryRes> relevanceComparator(String keyword) {
        if (keyword == null) {
            return null;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        Comparator<SummaryRes> byPrefixMatch = Comparator.comparing(
                (SummaryRes r) -> r.title().toLowerCase(Locale.ROOT).startsWith(lowerKeyword) ? 0 : 1);
        return byPrefixMatch.thenComparing(SummaryRes::title, String.CASE_INSENSITIVE_ORDER);
    }

    @Transactional(readOnly = true)
    public DetailRes getBySlug(String slug) {
        // Không phân biệt "không tồn tại" và "chưa PUBLISHED" — tránh lộ thông tin khóa
        // DRAFT/PENDING/REJECTED cho Guest chỉ vì họ đoán đúng slug.
        Course course = courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Course", slug));
        return mapToDetailRes(course);
    }

    private SummaryRes mapToSummaryRes(Course course) {
        return new SummaryRes(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getInstructor().getFullName(),
                course.getThumbnailUrl(),
                course.getLevel(),
                course.getPrice(),
                course.getIsFree(),
                course.getAvgRating(),
                courseReviewRepository.countByCourse_IdAndIsHiddenFalse(course.getId()),
                (int) lessonRepository.countByChapter_CourseId(course.getId()),
                course.getCategory().getSlug(),
                course.getCategory().getName()
        );
    }

    private DetailRes mapToDetailRes(Course course) {
        List<Chapter> chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAsc(course.getId());
        List<ChapterRes> chapterResList = chapters.stream()
                .map(chapter -> new ChapterRes(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getDisplayOrder(),
                        lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId()).stream()
                                .map(lesson -> new LessonRes(
                                        lesson.getId(),
                                        lesson.getTitle(),
                                        lesson.getDisplayOrder(),
                                        lesson.getIsPreview(),
                                        lesson.getDurationSec()
                                ))
                                .toList()
                ))
                .toList();

        return new DetailRes(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getDescription(),
                course.getInstructor().getFullName(),
                course.getThumbnailUrl(),
                course.getLevel(),
                course.getPrice(),
                course.getIsFree(),
                course.getAvgRating(),
                courseReviewRepository.countByCourse_IdAndIsHiddenFalse(course.getId()),
                course.getCategory().getSlug(),
                course.getCategory().getName(),
                chapterResList
        );
    }
}
