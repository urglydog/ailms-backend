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
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.util.SlugGenerator;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int MIN_LESSONS_TO_SUBMIT = 3;

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public DetailRes create(String instructorEmail, CreateReq req) {
        User instructor = userRepository.findByEmail(instructorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorEmail));
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

        return mapToDetailRes(courseRepository.save(course));
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

    @Transactional
    public void delete(String instructorEmail, Long id) {
        Course course = loadOwnedCourse(id, instructorEmail);

        boolean canHardDelete = course.getStatus() == CourseStatus.DRAFT
                && !enrollmentRepository.existsByCourseId(id);

        if (canHardDelete) {
            courseRepository.delete(course);
        } else {
            course.setStatus(CourseStatus.ARCHIVED);
            courseRepository.save(course);
        }
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

    /** BR-COURSE-01: điều kiện gửi duyệt — trả về danh sách điều kiện CHƯA đạt (rỗng = đủ điều kiện). */
    private List<String> computeMissingConditions(Course course) {
        List<String> missing = new ArrayList<>();
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
        // NOTE: đáng lẽ phải yêu cầu status=READY (đã có video hợp lệ), nhưng F2.1 chưa xử lý
        // video (đó là Giai đoạn 4 — UC34) nên Lesson không có cách nào đạt READY qua UI hiện tại.
        // Tạm thời chỉ đếm số lượng bài học; SIẾT LẠI thành điều kiện READY khi Giai đoạn 4 hoàn thành.
        long lessonCount = lessonRepository.countByChapter_CourseId(course.getId());
        if (lessonCount < MIN_LESSONS_TO_SUBMIT) {
            missing.add("Cần ít nhất " + MIN_LESSONS_TO_SUBMIT + " bài học (hiện có " + lessonCount + ")");
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
                course.getCreatedAt()
        );
    }

    private DetailRes mapToDetailRes(Course course) {
        List<Chapter> chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAsc(course.getId());
        List<ChapterDto.Res> chapterResList = chapters.stream()
                .map(chapter -> new ChapterDto.Res(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getDisplayOrder(),
                        lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId()).stream()
                                .map(lesson -> new LessonDto.Res(
                                        lesson.getId(),
                                        lesson.getTitle(),
                                        lesson.getDisplayOrder(),
                                        lesson.getIsPreview(),
                                        lesson.getStatus(),
                                        lesson.getVideoSource(),
                                        lesson.getVideoUrl()
                                ))
                                .toList()
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
                missingConditions.isEmpty()
        );
    }
}
