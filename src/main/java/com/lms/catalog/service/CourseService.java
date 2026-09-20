package com.lms.catalog.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.dto.ChapterDto;
import com.lms.catalog.dto.CourseDto.*;
import com.lms.catalog.dto.LessonDto;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.entity.CourseInvite;
import com.lms.catalog.repository.CourseInviteRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.util.SlugGenerator;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.instructor.repository.InstructorVerificationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Vòng đời khóa học (UC31, UC36, UC42) — Giảng viên tạo/sửa/gửi duyệt, Admin duyệt/từ chối.
 *
 * <p>{@code Course} không có collection {@code chapters} (thiết kế có chủ đích, xem
 * {@code Course.java}) nên điều kiện gửi duyệt (BR-COURSE-01) được tính bằng query đếm
 * ở đây thay vì một method trên entity.
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private static final Set<String> ALLOWED_LEVELS = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final int MAX_RESUBMIT_COUNT = 5;
    private static final int MIN_CHAPTERS_TO_SUBMIT = 1;
    private static final int MIN_LESSONS_TO_SUBMIT = 1;

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final InstructorVerificationRepository instructorVerificationRepository;
    private final CourseInviteRepository courseInviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final Tika tika = new Tika();

    @Transactional
    public DetailRes create(String instructorEmail, CreateReq req) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorEmail));

        if (instructor.getHeadline() == null || instructor.getHeadline().trim().isEmpty() 
            || instructor.getBio() == null || instructor.getBio().trim().length() < 20) {
            throw new BusinessRuleViolationException("PROFILE_INCOMPLETE", "Vui lòng hoàn thiện chức danh và tiểu sử giảng viên (tối thiểu 20 ký tự).");
        }

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.categoryId()));

        Course course = new Course();
        course.setTitle(req.title());
        course.setDescription(req.description());
        course.setSlug(generateUniqueSlug(req.title()));
        course.setLevel(resolveLevel(req.level()));
        course.setPrice(req.price());
        course.setIsFree(req.price().compareTo(BigDecimal.ZERO) == 0);
        course.setCategory(category);
        course.setInstructor(instructor);
        course.setStatus(CourseStatus.DRAFT);
        course.setReferralCode(generateReferralCode());

        return mapToDetailRes(courseRepository.save(course));
    }

    /** Chia doanh thu 2 mức (20/09/2026) — mã liên kết giới thiệu, sinh 1 LẦN duy nhất lúc
     * tạo khóa, không đổi sau đó. 10 ký tự hex (40 bit) đủ chống trùng cho quy mô đồ án. */
    private String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Transactional(readOnly = true)
    public Page<SummaryRes> getMine(String instructorEmail, CourseStatus status, Pageable pageable) {
        Page<Course> page = status == null
                ? courseRepository.findByInstructor_Email(instructorEmail, pageable)
                : courseRepository.findByInstructor_EmailAndStatus(instructorEmail, status, pageable);
        return page.map(this::mapToSummaryRes);
    }

    @Transactional(readOnly = true)
    public DetailRes getMineDetail(String instructorEmail, Long id) {
        Course course = loadOwnedCourse(id, instructorEmail);
        return mapToDetailRes(course);
    }

    @Transactional
    public DetailRes update(String instructorEmail, Long id, UpdateReq req) {
        Course course = loadOwnedCourse(id, instructorEmail);
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.categoryId()));

        // BR-COURSE-02: sửa metadata không cần duyệt lại, kể cả khi đã PUBLISHED.
        // Không tự đổi status khi đang REJECTED — instructor chủ động bấm gửi duyệt lại.
        course.setTitle(req.title());
        course.setDescription(req.description());
        course.setThumbnailUrl(req.thumbnailUrl());
        course.setLevel(resolveLevel(req.level()));
        course.setPrice(req.price());
        course.setIsFree(req.price().compareTo(BigDecimal.ZERO) == 0);
        course.setCategory(category);

        return mapToDetailRes(courseRepository.save(course));
    }

    @Transactional
    public DetailRes submitForReview(String instructorEmail, Long id) {
        Course course = loadOwnedCourse(id, instructorEmail);

        if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED) {
            throw new BusinessRuleViolationException(
                    "Chỉ có thể gửi duyệt khi khóa học đang ở trạng thái Nháp hoặc Bị từ chối");
        }

        List<String> missingConditions = computeMissingConditions(course);
        if (!missingConditions.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Khóa học chưa đủ điều kiện gửi duyệt: " + String.join("; ", missingConditions));
        }

        if (course.getStatus() == CourseStatus.REJECTED) {
            if (course.getResubmitCount() >= MAX_RESUBMIT_COUNT) {
                throw new BusinessRuleViolationException(
                        "Đã đạt giới hạn " + MAX_RESUBMIT_COUNT + " lần gửi duyệt lại cho khóa học này");
            }
            course.setResubmitCount(course.getResubmitCount() + 1);
        }

        course.setStatus(CourseStatus.PENDING);
        return mapToDetailRes(courseRepository.save(course));
    }

    /**
     * "Gỡ bỏ khóa học" (19/09/2026, sửa lại theo yêu cầu nghiệp vụ) — KHÔNG BAO GIỜ xoá cứng
     * dữ liệu nữa (trước đây khóa DRAFT chưa có học viên bị xoá thật, dọn cả chương/bài/video
     * trên B2) — đúng nghiệp vụ LMS: dữ liệu khóa học của Giảng viên không được phép mất. Luôn
     * chuyển sang {@code ARCHIVED}: học viên MỚI không tìm/ghi danh được nữa, học viên ĐÃ ghi
     * danh vẫn giữ nguyên quyền truy cập (BR-ENROLL-03). Lưu lại {@code previousStatus} để
     * {@link #reactivate} khôi phục đúng trạng thái trước đó.
     */
    @Transactional
    public DetailRes delete(String instructorEmail, Long id) {
        Course course = loadOwnedCourse(id, instructorEmail);
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("Khóa học đã được lưu trữ từ trước");
        }
        course.setPreviousStatus(course.getStatus());
        course.setStatus(CourseStatus.ARCHIVED);
        return mapToDetailRes(courseRepository.save(course));
    }

    /** "Kích hoạt lại" (19/09/2026, tính năng mới) — khôi phục đúng trạng thái TRƯỚC khi bị lưu
     * trữ (DRAFT nếu chưa từng xuất bản, PUBLISHED nếu đã từng — không cần Admin duyệt lại vì
     * bản thân nội dung không đổi trong lúc lưu trữ). */
    @Transactional
    public DetailRes reactivate(String instructorEmail, Long id) {
        Course course = loadOwnedCourse(id, instructorEmail);
        if (course.getStatus() != CourseStatus.ARCHIVED) {
            throw new BusinessRuleViolationException("Chỉ có thể kích hoạt lại khóa học đang ở trạng thái lưu trữ");
        }
        course.setStatus(course.getPreviousStatus() != null ? course.getPreviousStatus() : CourseStatus.DRAFT);
        course.setPreviousStatus(null);
        return mapToDetailRes(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public Page<SummaryRes> getModerationList(CourseStatus status, Pageable pageable) {
        CourseStatus effectiveStatus = status == null ? CourseStatus.PENDING : status;
        return courseRepository.findByStatus(effectiveStatus, pageable).map(this::mapToSummaryRes);
    }

    @Transactional(readOnly = true)
    public DetailRes getModerationDetail(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        return mapToDetailRes(course);
    }

    @Transactional
    public DetailRes approve(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new BusinessRuleViolationException("Chỉ có thể duyệt khóa học đang ở trạng thái Chờ duyệt");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        return mapToDetailRes(courseRepository.save(course));
    }

    @Transactional
    public DetailRes reject(Long id, RejectReq req) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new BusinessRuleViolationException("Chỉ có thể từ chối khóa học đang ở trạng thái Chờ duyệt");
        }
        course.setStatus(CourseStatus.REJECTED);
        course.setRejectReason(req.reason());
        return mapToDetailRes(courseRepository.save(course));
    }

    // ---- helpers ----

    /**
     * Ảnh bìa khóa học (mở rộng theo yêu cầu Giai đoạn 4 — dùng chung hạ tầng B2 vừa dựng cho
     * UC34/UC35, thay ô nhập URL text tạm thời của F2.1). 5MB là ngưỡng thực dụng, không phải
     * business rule chính thức trong tài liệu đặc tả.
     */
    @Transactional
    public DetailRes uploadThumbnail(String instructorEmail, Long id, MultipartFile file) {
        Course course = loadOwnedCourse(id, instructorEmail);

        if (file.isEmpty()) {
            throw new InvalidRequestException("File ảnh trống");
        }
        long maxBytes = 5L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("Ảnh bìa vượt quá 5MB");
        }

        String detectedMime;
        try (InputStream sniff = file.getInputStream()) {
            detectedMime = tika.detect(sniff);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file ảnh: " + e.getMessage());
        }
        String extension = switch (detectedMime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new InvalidRequestException("Chỉ chấp nhận ảnh JPEG/PNG/WEBP");
        };

        String key = "thumbnails/" + id + "/" + UUID.randomUUID() + "." + extension;
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được ảnh lên kho lưu trữ: " + e.getMessage());
        }

        course.setThumbnailUrl(url);
        return mapToDetailRes(courseRepository.save(course));
    }

    /**
     * "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — {@code password} rỗng khi chuyển
     * sang PRIVATE_PASSWORD nghĩa là GIỮ NGUYÊN mật khẩu cũ (không bắt nhập lại mỗi lần đổi
     * field khác); bắt buộc nhập nếu đây là LẦN ĐẦU đặt (chưa có hash nào).
     */
    @Transactional
    public DetailRes updateVisibility(String instructorEmail, Long id, VisibilityUpdateReq req) {
        Course course = loadOwnedCourse(id, instructorEmail);
        course.setVisibility(req.visibility());

        if (req.visibility() == CourseVisibility.PRIVATE_PASSWORD) {
            if (req.password() != null && !req.password().isBlank()) {
                course.setEnrollPasswordHash(passwordEncoder.encode(req.password()));
            } else if (course.getEnrollPasswordHash() == null) {
                throw new InvalidRequestException(
                        "Cần đặt mật khẩu đăng ký khi chọn chế độ Riêng tư (mật khẩu)");
            }
        } else {
            course.setEnrollPasswordHash(null);
        }

        return mapToDetailRes(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<String> listInvites(String instructorEmail, Long id) {
        loadOwnedCourse(id, instructorEmail);
        return courseInviteRepository.findByCourse_IdOrderByCreatedAtDesc(id).stream()
                .map(CourseInvite::getEmail)
                .toList();
    }

    @Transactional
    public void addInvite(String instructorEmail, Long id, String email) {
        Course course = loadOwnedCourse(id, instructorEmail);
        String normalized = email.trim().toLowerCase();
        if (courseInviteRepository.existsByCourse_IdAndEmail(id, normalized)) {
            return; // idempotent, giống BR-CART-02: mời lại người đã mời không báo lỗi.
        }
        CourseInvite invite = new CourseInvite();
        invite.setCourse(course);
        invite.setEmail(normalized);
        courseInviteRepository.save(invite);
    }

    @Transactional
    public void removeInvite(String instructorEmail, Long id, String email) {
        loadOwnedCourse(id, instructorEmail);
        courseInviteRepository.deleteByCourse_IdAndEmail(id, email.trim().toLowerCase());
    }

    private Course loadOwnedCourse(Long id, String instructorEmail) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        if (!course.getInstructor().getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên khóa học này");
        }
        return course;
    }

    private String resolveLevel(String level) {
        if (level == null || level.isBlank()) {
            return "BEGINNER";
        }
        String upper = level.toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_LEVELS.contains(upper)) {
            throw new InvalidRequestException("Trình độ không hợp lệ: " + level);
        }
        return upper;
    }

    /**
     * Slug bị cấm vì trùng path literal của chính controller này ({@code /courses/mine},
     * {@code /courses/moderation}) — Spring ưu tiên path literal hơn {@code /courses/{slug}}
     * (F2.2), nên một khóa học lỡ trùng slug này sẽ vĩnh viễn không truy cập được qua slug.
     */
    private static final Set<String> RESERVED_SLUGS = Set.of("mine", "moderation");

    /** Slug sinh 1 lần lúc tạo, không đổi lại khi sửa tiêu đề (giữ URL ổn định). */
    private String generateUniqueSlug(String title) {
        String base = SlugGenerator.slugify(title);
        String candidate = base;
        int suffix = 2;
        while (courseRepository.existsBySlug(candidate) || RESERVED_SLUGS.contains(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /**
     * BR-COURSE-01: điều kiện gửi duyệt — trả về danh sách điều kiện CHƯA đạt (rỗng = đủ điều kiện).
     * Gộp cả BR-VERIFY-01 (15/09/2026) vào đây thay vì chặn riêng ở {@link #submitForReview} —
     * để FE hiện được ngay trong checklist "Điều kiện gửi duyệt" (component có sẵn, xem
     * {@code SubmitChecklist.tsx}) thay vì instructor chỉ biết lúc bấm nút và nhận lỗi bất ngờ.
     */
    private List<String> computeMissingConditions(Course course) {
        List<String> missing = new ArrayList<>();

        // BR-VERIFY-01 (19/09/2026, khôi phục — bị mất trong lúc merge PR #132): chặn 1 LẦN DUY
        // NHẤT/tài khoản — kiểm tra "đã có bản ghi xác minh chưa" thay vì đếm số khóa học trước
        // đó, nên 1 khi đã xác minh thì mọi khóa (kể cả khóa đầu tiên tiếp theo) đều qua được.
        if (!instructorVerificationRepository.existsByUser_Id(course.getInstructor().getId())) {
            missing.add("Chưa hoàn tất xác minh thông tin định danh (BR-VERIFY-01)");
        }
        if (course.getTitle() == null || course.getTitle().isBlank()) {
            missing.add("Chưa có tiêu đề");
        }
        if (course.getDescription() == null || course.getDescription().isBlank()) {
            missing.add("Chưa có mô tả");
        }
        if (course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()) {
            missing.add("Chưa có ảnh bìa");
        }
        long chapterCount = chapterRepository.countByCourseId(course.getId());
        if (chapterCount < MIN_CHAPTERS_TO_SUBMIT) {
            missing.add("Cần ít nhất " + MIN_CHAPTERS_TO_SUBMIT + " chương");
        }
        // Giai đoạn 4: bài học chỉ tính hợp lệ khi đã có video (status=READY, UC34).
        long readyLessonCount = lessonRepository.countByChapter_CourseIdAndStatus(course.getId(), "READY");
        if (readyLessonCount < MIN_LESSONS_TO_SUBMIT) {
            missing.add("Cần ít nhất " + MIN_LESSONS_TO_SUBMIT
                    + " bài học đã có video sẵn sàng (hiện có " + readyLessonCount + ")");
        }
        return missing;
    }

    private SummaryRes mapToSummaryRes(Course course) {
        return new SummaryRes(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getStatus(),
                course.getThumbnailUrl(),
                course.getCategory().getName(),
                course.getPrice(),
                course.getIsFree(),
                course.getAvgRating(),
                course.getTotalLessons(),
                course.getCreatedAt(),
                computeCompletionPercent(course)
        );
    }

    /** Xem docblock {@code SummaryRes.completionPercent}. */
    private int computeCompletionPercent(Course course) {
        int total = 5;
        int met = 0;
        if (course.getTitle() != null && !course.getTitle().isBlank()) met++;
        if (course.getDescription() != null && !course.getDescription().isBlank()) met++;
        if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isBlank()) met++;
        if (chapterRepository.countByCourseId(course.getId()) >= MIN_CHAPTERS_TO_SUBMIT) met++;
        if (lessonRepository.countByChapter_CourseIdAndStatus(course.getId(), "READY") >= MIN_LESSONS_TO_SUBMIT) met++;
        return Math.round(met * 100f / total);
    }

    private DetailRes mapToDetailRes(Course course) {
        List<Chapter> chapters = chapterRepository.findChaptersWithLessonsByCourseId(course.getId());
        List<ChapterDto.Res> chapterResList = chapters.stream()
                .map(chapter -> new ChapterDto.Res(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getDisplayOrder(),
                        chapter.getLessons() == null ? java.util.Collections.emptyList() : chapter.getLessons().stream()
                                .map(lesson -> new LessonDto.Res(
                                        lesson.getId(),
                                        lesson.getTitle(),
                                        lesson.getDisplayOrder(),
                                        lesson.getIsPreview(),
                                        lesson.getStatus(),
                                        lesson.getVideoSource(),
                                        lesson.getVideoUrl(),
                                        lesson.getYoutubeId(),
                                        lesson.getDurationSec(),
                                        lesson.getDescription()
                                ))
                                .toList(),
                        chapter.getDescription()
                ))
                .toList();

        List<String> missingConditions = computeMissingConditions(course);

        return new DetailRes(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getDescription(),
                course.getThumbnailUrl(),
                course.getLevel(),
                course.getPrice(),
                course.getIsFree(),
                course.getStatus(),
                course.getRejectReason(),
                course.getResubmitCount(),
                course.getCategory().getId(),
                course.getCategory().getName(),
                course.getInstructor().getId(),
                course.getInstructor().getFullName(),
                chapterResList,
                missingConditions,
                missingConditions.isEmpty(),
                course.getVisibility(),
                course.getEnrollPasswordHash() != null,
                course.getReferralCode()
        );
    }
}
