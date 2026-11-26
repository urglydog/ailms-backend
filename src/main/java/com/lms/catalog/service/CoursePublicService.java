package com.lms.catalog.service;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.lms.catalog.repository.projection.CourseLessonStats;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
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

    // Self-injection QUA SETTER (không qua constructor — giữ nguyên @RequiredArgsConstructor,
    // không đụng test @InjectMocks cũ). Bắt buộc để gọi loadCachedDetail() QUA proxy Spring AOP,
    // vì gọi this.loadCachedDetail() trực tiếp trong cùng class sẽ ÂM THẦM bỏ qua @Cacheable.
    private CoursePublicService self;

    @Autowired
    public void setSelf(@Lazy CoursePublicService self) {
        this.self = self;
    }

    @Cacheable(value = "publicCourseSearch", condition = "#keyword == null and #pageable.pageNumber == 0")
    @Transactional(readOnly = true)
    public SearchSliceRes search(
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

        // (08/11/2026) Sắp xếp nằm TRONG câu truy vấn — trước đây sắp lại bằng Comparator trên từng trang đã
        // fetch, đúng khi chỉ có 1 trang lớn nhưng sai thứ tự giữa các trang khi tải dần. Sort do client gửi
        // lên bị bỏ qua: chỉ nhận đúng 4 lựa chọn ở dưới (tránh sắp theo cột tùy ý).
        String sort = sortBy == null ? "" : sortBy;
        Slice<Course> slice;
        if ("reviews".equals(sort)) {
            slice = courseRepository.searchPublicByReviews(
                    normalizedKeyword, categorySlug, normalizedLevel, isFree, minRatingDecimal,
                    durationRange.minSec(), durationRange.maxSec(), unsorted(pageable));
        } else if ("relevance".equals(sort) && normalizedKeyword != null) {
            slice = courseRepository.searchPublicByRelevance(
                    normalizedKeyword, categorySlug, normalizedLevel, isFree, minRatingDecimal,
                    durationRange.minSec(), durationRange.maxSec(), unsorted(pageable));
        } else {
            Sort order = "rating".equals(sort)
                    ? Sort.by(Sort.Order.desc("avgRating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                    : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            slice = courseRepository.searchPublic(
                    normalizedKeyword, categorySlug, normalizedLevel, isFree, minRatingDecimal,
                    durationRange.minSec(), durationRange.maxSec(),
                    PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order));
        }
        List<SummaryRes> content = mapSummaries(slice.getContent());
        return new SearchSliceRes(content, slice.getNumber(), slice.getSize(), !slice.hasNext());
    }

    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
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

    /**
     * @param requesterEmail null nếu Guest chưa đăng nhập — vẫn xem được khóa PUBLIC/
     *                       PRIVATE_PASSWORD (mật khẩu chỉ chặn lúc GHI DANH, không chặn xem
     *                       trang chi tiết), nhưng KHÔNG xem được khóa PRIVATE_INVITE trừ khi
     *                       email nằm trong danh sách mời (BR mới, 19/09/2026).
     */
    @Transactional(readOnly = true)
    public DetailRes getBySlug(String slug, String requesterEmail) {
        // Kiểm tra quyền xem PHẢI chạy MỌI lần gọi, không được cache — bug thật (29/09/2026):
        // trước đây @Cacheable bọc nguyên method này, nên với khóa PRIVATE_INVITE, sau khi 1
        // người được mời xem thành công, TOÀN BỘ nội dung khóa bị cache theo slug và bất kỳ ai
        // khác (kể cả Guest) gọi lại slug đó trong TTL sẽ nhận thẳng data từ cache, bỏ qua hoàn
        // toàn bước kiểm tra mời bên dưới — lộ nội dung VÀ lộ luôn sự tồn tại của khóa riêng tư.
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

        // Nội dung khóa học (courses/chapters/lessons/giá hiển thị) giống hệt nhau cho MỌI
        // người được phép xem — không có field cá nhân hóa theo requester trong DetailRes, nên
        // an toàn để cache theo slug, tách biệt khỏi bước kiểm tra quyền ở trên. Gọi QUA self
        // (proxy) — gọi trực tiếp loadCachedDetail(slug) ở đây sẽ không đi qua @Cacheable.
        return self.loadCachedDetail(slug);
    }

    /** Chỉ gọi từ {@link #getBySlug} SAU KHI đã qua kiểm tra quyền — bản thân method này không
     * biết gì về requester, chỉ cache dữ liệu công khai theo slug. */
    @Cacheable(value = "courseDetails", key = "#slug")
    @Transactional(readOnly = true)
    public DetailRes loadCachedDetail(String slug) {
        Course course = courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Course", slug));
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

    /**
     * Map một trang khóa học (hiệu năng đợt 2, PLAN_HieuNang_HeThong.md): category + instructor đã nạp sẵn
     * bằng @EntityGraph; số đánh giá, số bài + tổng thời lượng và giá sau coupon lấy THEO LÔ cho cả trang
     * (vài truy vấn cố định) thay vì 5–6 truy vấn cho MỖI khóa như trước.
     */
    /** Dựng danh sách tóm tắt công khai (giá/giảm giá/đánh giá...) cho các khóa đã nạp sẵn — dùng bởi kệ "Được đề xuất" (Giai đoạn E). */
    @Transactional(readOnly = true)
    public List<SummaryRes> summariesOf(List<Course> courses) {
        return mapSummaries(courses);
    }

    private List<SummaryRes> mapSummaries(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> ids = courses.stream().map(Course::getId).toList();
        Map<Long, Long> reviewCounts = new HashMap<>();
        courseReviewRepository.countVisibleByCourseIds(ids).forEach(r -> reviewCounts.put(r.courseId(), r.count()));
        Map<Long, CourseLessonStats> lessonStats = new HashMap<>();
        lessonRepository.findLessonStatsByCourseIds(ids).forEach(st -> lessonStats.put(st.courseId(), st));
        Map<Long, PriceRes> prices = couponService.getDisplayPrices(courses);

        List<SummaryRes> out = new ArrayList<>(courses.size());
        for (Course course : courses) {
            CourseLessonStats st = lessonStats.get(course.getId());
            PriceRes price = prices.get(course.getId());
            out.add(new SummaryRes(
                    course.getId(),
                    course.getTitle(),
                    course.getSlug(),
                    course.getInstructor().getFullName(),
                    course.getThumbnailUrl(),
                    course.getLevel(),
                    course.getPrice(),
                    course.getIsFree(),
                    course.getAvgRating(),
                    reviewCounts.getOrDefault(course.getId(), 0L),
                    st == null ? 0 : st.lessonCount().intValue(),
                    st == null ? 0 : st.totalDurationSec().intValue(),
                    course.getCategory().getSlug(),
                    course.getCategory().getName(),
                    price == null ? course.getPrice() : price.finalPrice(),
                    price == null ? null : price.discountPercent()));
        }
        return out;
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
                course.getInstructor().getAvatarUrl(),
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
                course.getVisibility() == CourseVisibility.PRIVATE_PASSWORD,
                course.getLearningOutcomes() == null ? List.of() : List.copyOf(course.getLearningOutcomes()),
                course.getRequirements() == null ? List.of() : List.copyOf(course.getRequirements()),
                course.getTargetAudience() == null ? List.of() : List.copyOf(course.getTargetAudience()),
                price.discountEndsAt()
        );
    }

    /** Nhãn hiển thị sinh từ mã ngôn ngữ — KHÔNG hardcode danh sách (BR-DUB-07), giống {@code LessonPlayerService}. */
    private String displayLabel(String languageCode) {
        return Locale.forLanguageTag(languageCode).getDisplayName(Locale.forLanguageTag("vi-VN"));
    }
}
