package com.lms.catalog.service;

import com.lms.catalog.dto.CoursePublicDto.*;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.coupon.dto.CouponDto.PriceRes;
import com.lms.coupon.service.CouponService;
import com.lms.dubbing.repository.AudioTrackRepository;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
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
    private final AudioTrackRepository audioTrackRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CouponService couponService;
    private final CourseAccessService courseAccessService;

    @Transactional(readOnly = true)
    public Page<SummaryRes> search(
            String keyword, String categorySlug, String level, String priceType, Double minRating,
            String durationBucket, String sortBy, Pageable pageable) {
        Boolean isFree = switch (priceType == null ? "" : priceType) {
            case "free" -> Boolean.TRUE;
            case "paid" -> Boolean.FALSE;
            default -> null;
        };
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String normalizedLevel = (level == null || level.isBlank()) ? null : level.toUpperCase(Locale.ROOT);
        java.math.BigDecimal minRatingDecimal = minRating == null ? null : java.math.BigDecimal.valueOf(minRating);
        DurationRange durationRange = resolveDurationRange(durationBucket);

        // `pageable` vẫn giữ nguyên sort mặc định (createdAt DESC, xem @PageableDefault ở
        // Controller) — đúng cho "Mới nhất". 3 lựa chọn còn lại (rating/reviews/relevance) không
        // ứng với 1 cột duy nhất (reviewCount không lưu sẵn, relevance cần so khớp từ khóa) nên
        // được sắp lại bằng Comparator ngay trên trang đã fetch. Chỉ đúng ở quy mô hiện tại (fetch
        // 1 trang lớn duy nhất, chưa có phân trang thật) — cần viết lại bằng subquery SQL nếu
        // catalog lớn tới mức cần phân trang thật.
        Page<Course> page = courseRepository.searchPublic(
                normalizedKeyword, categorySlug, normalizedLevel, isFree, minRatingDecimal,
                durationRange.minSec(), durationRange.maxSec(), pageable);
        List<SummaryRes> content = page.getContent().stream().map(this::mapToSummaryRes).collect(Collectors.toList());

        Comparator<SummaryRes> comparator = resolveComparator(sortBy, normalizedKeyword);
        if (comparator != null) {
            content.sort(comparator);
        }

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    private record DurationRange(Integer minSec, Integer maxSec) {}

    /**
     * Bộ lọc "Video Duration" kiểu Udemy (14/09/2026, mở rộng) — 5 khoảng cố định giống ảnh
     * tham khảo. Giá trị lạ/không khớp bucket nào coi như không lọc (an toàn, không ném lỗi).
     */
    private DurationRange resolveDurationRange(String durationBucket) {
        return switch (durationBucket == null ? "" : durationBucket) {
            case "0-1" -> new DurationRange(null, 3600);
            case "1-3" -> new DurationRange(3600, 10800);
            case "3-6" -> new DurationRange(10800, 21600);
            case "6-17" -> new DurationRange(21600, 61200);
            case "17+" -> new DurationRange(61200, null);
            default -> new DurationRange(null, null);
        };
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

    /**
     * @param requesterEmail null nếu Guest chưa đăng nhập — vẫn xem được khóa PUBLIC/
     *                       PRIVATE_PASSWORD (mật khẩu chỉ chặn lúc GHI DANH, không chặn xem
     *                       trang chi tiết), nhưng KHÔNG xem được khóa PRIVATE_INVITE trừ khi
     *                       email nằm trong danh sách mời (BR mới, 19/09/2026).
     */
    @Transactional(readOnly = true)
    public DetailRes getBySlug(String slug, String requesterEmail) {
        // Không phân biệt "không tồn tại" và "chưa PUBLISHED" — tránh lộ thông tin khóa
        // DRAFT/PENDING/REJECTED cho Guest chỉ vì họ đoán đúng slug. Áp dụng cùng nguyên tắc cho
        // PRIVATE_INVITE: người ngoài không được biết khóa "riêng tư mời" này có tồn tại hay không.
        Course course = courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Course", slug));

        if (course.getVisibility() == CourseVisibility.PRIVATE_INVITE) {
            boolean allowed = courseAccessService.isInvited(course, requesterEmail)
                    || (requesterEmail != null
                            && enrollmentRepository.existsByUser_EmailAndCourse_Id(requesterEmail, course.getId()));
            if (!allowed) {
                throw new ResourceNotFoundException("Course", slug);
            }
        }

        return mapToDetailRes(course);
    }

    /**
     * UC11 — Học thử Preview. Guest/Student chưa sở hữu khóa học chỉ xem được bài đánh dấu
     * {@code isPreview} (BR-ENROLL-02). Chưa có luồng ghi danh thật (Giai đoạn 3, xem
     * {@code CoursePublicService#getBySlug} — {@code enrolled} luôn trả {@code false}) nên tạm
     * thời CHỈ mở nhánh Preview; khi có ghi danh thật, bổ sung nhánh "đã sở hữu khóa học" ở đây.
     */
    @Transactional(readOnly = true)
    public PlayerRes getLessonForPlayback(Long lessonId, String requesterEmail) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        Course course = lesson.getChapter().getCourse();
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        // (19/09/2026) — khóa "Riêng tư mời" không lộ preview cho người ngoài, cùng tinh thần
        // với `getBySlug`. Khóa "Riêng tư mật khẩu" vẫn cho xem thử preview bình thường (mật
        // khẩu chỉ chặn lúc ghi danh thật).
        if (course.getVisibility() == CourseVisibility.PRIVATE_INVITE
                && !courseAccessService.isInvited(course, requesterEmail)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        if (!Boolean.TRUE.equals(lesson.getIsPreview())) {
            throw new AccessDeniedDomainException(
                    "Bài học này yêu cầu sở hữu khóa học. Chỉ bài học Học thử (Preview) mới xem được khi chưa đăng ký.");
        }
        return new PlayerRes(
                lesson.getId(),
                lesson.getTitle(),
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                lesson.getVideoSource(),
                lesson.getVideoUrl(),
                lesson.getYoutubeId(),
                lesson.getDurationSec()
        );
    }

    private SummaryRes mapToSummaryRes(Course course) {
        PriceRes price = resolveDisplayPrice(course);
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
                lessonRepository.sumDurationSecByCourseId(course.getId()),
                course.getCategory().getSlug(),
                course.getCategory().getName(),
                price.finalPrice(),
                price.discountPercent()
        );
    }

    /** UC57 mở rộng (15/09/2026) — khóa miễn phí bỏ qua tính coupon (giảm giá trên 0đ vô nghĩa). */
    private PriceRes resolveDisplayPrice(Course course) {
        if (Boolean.TRUE.equals(course.getIsFree())) {
            return new PriceRes(course.getPrice(), course.getPrice(), null, null, true);
        }
        return couponService.getDisplayPrice(course);
    }

    private DetailRes mapToDetailRes(Course course) {
        List<Chapter> chapters = chapterRepository.findChaptersWithLessonsByCourseId(course.getId());
        List<ChapterRes> chapterResList = chapters.stream()
                .map(chapter -> new ChapterRes(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getDisplayOrder(),
                        chapter.getLessons() == null ? java.util.Collections.emptyList() : chapter.getLessons().stream()
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

        String sourceLanguage = lessonRepository
                .findFirstByChapter_CourseIdAndSourceLanguageIsNotNullOrderByChapter_DisplayOrderAscDisplayOrderAsc(course.getId())
                .map(lesson -> displayLabel(lesson.getSourceLanguage()))
                .orElse(null);
        List<String> dubbedLanguages = audioTrackRepository.findAvailableLanguagesByCourse(course.getId()).stream()
                .map(this::displayLabel)
                .toList();
        long learnerCount = enrollmentRepository.countByCourseId(course.getId());
        PriceRes price = resolveDisplayPrice(course);

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
                lessonRepository.sumDurationSecByCourseId(course.getId()),
                course.getCategory().getSlug(),
                course.getCategory().getName(),
                chapterResList,
                course.getUpdatedAt(),
                sourceLanguage,
                dubbedLanguages,
                learnerCount,
                price.finalPrice(),
                price.discountPercent(),
                course.getVisibility() == CourseVisibility.PRIVATE_PASSWORD
        );
    }

    /** Nhãn hiển thị sinh từ mã ngôn ngữ — KHÔNG hardcode danh sách (BR-DUB-07), giống {@code LessonPlayerService}. */
    private String displayLabel(String languageCode) {
        return Locale.forLanguageTag(languageCode).getDisplayName(Locale.forLanguageTag("vi-VN"));
    }
}
