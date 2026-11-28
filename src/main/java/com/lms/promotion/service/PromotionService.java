package com.lms.promotion.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.catalog.service.CoursePublicService;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.CourseVisibility;
import com.lms.common.enums.PromotionPlacement;
import com.lms.common.enums.PromotionStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.util.CacheEvictionHelper;
import com.lms.finance.service.FinanceAuditService;
import com.lms.payment.repository.PaymentRepository;
import com.lms.promotion.dto.PromotionDto;
import com.lms.promotion.entity.CoursePromotion;
import com.lms.promotion.repository.CoursePromotionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chương trình quảng bá của nền tảng (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn E) — cơ sở để biện minh phần nền tảng giữ
 * lại ở đơn organic: nền tảng chủ động đưa khóa của giảng viên đã đăng ký lên kệ "Được đề xuất". TỰ NGUYỆN: giảng viên đăng ký, admin
 * duyệt kèm khung thời gian + độ ưu tiên. Kệ hiển thị luôn có nhãn "Được đề xuất" ở FE (minh bạch với học viên).
 */
@Service
@RequiredArgsConstructor
public class PromotionService {

    static final int MAX_DAYS = 90;
    static final int MIN_LESSONS = 3;
    static final int MAX_FEATURED_SIZE = 24;
    /** Số khóa tối đa lấy ra làm "bể" để xoay vòng trước khi cắt theo `size`. */
    static final int FEATURED_POOL_SIZE = 100;
    /** Cùng định dạng các thông báo/email khác của hệ thống (vd. PayoutService, EmailService). */
    private static final DateTimeFormatter NOTIFY_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final Set<PromotionStatus> OPEN_STATUSES = Set.of(PromotionStatus.REQUESTED, PromotionStatus.APPROVED);
    private static final Pattern CATEGORY_SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,99}$");

    private final CoursePromotionRepository promotionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final CoursePublicService coursePublicService;
    private final CacheEvictionHelper cacheEvictionHelper;
    private final NotificationService notificationService;
    private final FinanceAuditService auditService;
    private final LessonRepository lessonRepository;
    private final com.lms.finance.debt.DebtGuard debtGuard;

    // ── Giảng viên ──────────────────────────────────────────────────────

    @Transactional
    public PromotionDto.Res request(String email, PromotionDto.CreateReq req) {
        User user = requireUser(email);
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));
        if (course.getInstructor() == null || !course.getInstructor().getId().equals(user.getId())) {
            throw new AccessDeniedDomainException("Bạn chỉ đăng ký quảng bá cho khóa học của chính mình");
        }
        assertEligible(course);
        debtGuard.assertCanPromote(user.getId());
        if (promotionRepository.existsOpen(course.getId(), req.placement(), java.time.LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Khóa này đã có đăng ký quảng bá cho vị trí này đang chờ duyệt hoặc đang chạy");
        }
        CoursePromotion p = new CoursePromotion();
        p.setCourse(course);
        p.setPlacement(req.placement());
        p.setStatus(PromotionStatus.REQUESTED);
        p.setRequestedBy(user);
        p.setRequestNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        CoursePromotion saved = promotionRepository.save(p);

        userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), "PROMOTION_REQUESTED",
                "Đăng ký quảng bá mới", user.getFullName() + " đăng ký quảng bá khóa \"" + course.getTitle() + "\".",
                "/admin/promotions"));
        // Giảng viên cũng nhận xác nhận đã gửi (trước đây chỉ admin được báo).
        notificationService.notify(user.getId(), "PROMOTION_SUBMITTED", "Đã gửi đăng ký quảng bá",
                "Đăng ký quảng bá khóa \"" + course.getTitle() + "\" đang chờ quản trị viên duyệt.", "/instructor/share");
        return toRes(saved, false);
    }

    @Transactional(readOnly = true)
    public List<PromotionDto.Res> listMine(String email) {
        User user = requireUser(email);
        return promotionRepository.findByRequestedBy_IdOrderByCreatedAtDesc(user.getId()).stream().map(p -> toRes(p, false)).toList();
    }

    @Transactional
    public PromotionDto.Res cancel(String email, Long id) {
        CoursePromotion p = require(id);
        if (!p.getRequestedBy().getEmail().equals(email)) {
            throw new AccessDeniedDomainException("Bạn chỉ hủy được đăng ký của chính mình");
        }
        requireStatus(p, OPEN_STATUSES, "Chỉ hủy được đăng ký đang chờ duyệt hoặc đang chạy");
        boolean wasRunning = p.getStatus() == PromotionStatus.APPROVED;
        String before = p.getStatus().name();
        p.setStatus(PromotionStatus.CANCELLED);
        if (wasRunning && p.getEndAt() != null && p.getEndAt().isAfter(LocalDateTime.now())) {
            p.setEndAt(LocalDateTime.now());
        }
        CoursePromotion saved = promotionRepository.save(p);
        audit(email, "PROMOTION_CANCELLED_BY_INSTRUCTOR", saved, before);
        cacheEvictionHelper.evictCourseCacheAfterCommit(p.getCourse().getSlug());
        if (wasRunning) { // giảng viên chủ động gỡ đợt đang chạy → báo admin biết
            userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), "PROMOTION_WITHDRAWN",
                    "Giảng viên gỡ quảng bá", p.getRequestedBy().getFullName() + " đã gỡ quảng bá khóa \"" + p.getCourse().getTitle() + "\".",
                    "/admin/promotions"));
        }
        return toRes(saved, false);
    }

    // ── Admin ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PromotionDto.Res> listForAdmin(PromotionStatus status) {
        List<CoursePromotion> rows = status == null
                ? promotionRepository.findAllByOrderByCreatedAtDesc()
                : promotionRepository.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(p -> toRes(p, true)).toList();
    }

    @Transactional
    public PromotionDto.Res approve(String adminEmail, Long id, PromotionDto.ApproveReq req) {
        CoursePromotion p = require(id);
        requireStatus(p, Set.of(PromotionStatus.REQUESTED), "Chỉ duyệt được đăng ký đang chờ duyệt");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = req.startAt() == null ? now : req.startAt();
        if (!req.endAt().isAfter(start)) {
            throw new InvalidRequestException("Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
        if (!req.endAt().isAfter(now)) {
            throw new InvalidRequestException("Thời điểm kết thúc phải ở tương lai");
        }
        if (Duration.between(start, req.endAt()).compareTo(Duration.ofDays(MAX_DAYS)) > 0) { // so chính xác, không cắt phần lẻ như toDays()
            throw new InvalidRequestException("Mỗi đợt quảng bá tối đa " + MAX_DAYS + " ngày");
        }
        assertEligible(p.getCourse());
        User admin = requireUser(adminEmail);
        p.setStatus(PromotionStatus.APPROVED);
        p.setStartAt(start);
        p.setEndAt(req.endAt());
        p.setAdminNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        p.setReviewedBy(admin);
        p.setReviewedAt(now);
        CoursePromotion saved = promotionRepository.save(p);

        audit(adminEmail, "PROMOTION_APPROVED", saved, "REQUESTED");
        cacheEvictionHelper.evictCourseCacheAfterCommit(p.getCourse().getSlug());
        notificationService.notify(p.getRequestedBy().getId(), "PROMOTION_APPROVED", "Khóa học của bạn được quảng bá",
                "Khóa \"" + p.getCourse().getTitle() + "\" sẽ được đề xuất đến " + req.endAt().format(NOTIFY_TIME) + ".", "/instructor/share");
        return toRes(saved, true);
    }

    @Transactional
    public PromotionDto.Res reject(String adminEmail, Long id, String reason) {
        CoursePromotion p = require(id);
        requireStatus(p, Set.of(PromotionStatus.REQUESTED), "Chỉ từ chối được đăng ký đang chờ duyệt");
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Cần nhập lý do");
        }
        User admin = requireUser(adminEmail);
        p.setStatus(PromotionStatus.REJECTED);
        p.setAdminNote(reason.trim());
        p.setReviewedBy(admin);
        p.setReviewedAt(LocalDateTime.now());
        CoursePromotion saved = promotionRepository.save(p);
        audit(adminEmail, "PROMOTION_REJECTED", saved, "REQUESTED");
        notificationService.notify(p.getRequestedBy().getId(), "PROMOTION_REJECTED", "Đăng ký quảng bá chưa được duyệt",
                "Khóa \"" + p.getCourse().getTitle() + "\": " + reason.trim(), "/instructor/share");
        return toRes(saved, true);
    }

    /** Gỡ sớm một đợt đang chạy (vd. phát hiện vi phạm). BẮT BUỘC có lý do — giảng viên được thông báo kèm lý do này. */
    @Transactional
    public PromotionDto.Res end(String adminEmail, Long id, String reason) {
        CoursePromotion p = require(id);
        requireStatus(p, Set.of(PromotionStatus.APPROVED), "Chỉ kết thúc được đợt đã duyệt");
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Cần nhập lý do gỡ quảng bá");
        }
        p.setStatus(PromotionStatus.ENDED);
        p.setAdminNote(reason.trim());
        p.setEndAt(LocalDateTime.now());
        CoursePromotion saved = promotionRepository.save(p);
        audit(adminEmail, "PROMOTION_ENDED", saved, "APPROVED");
        cacheEvictionHelper.evictCourseCacheAfterCommit(p.getCourse().getSlug());
        notificationService.notify(p.getRequestedBy().getId(), "PROMOTION_ENDED", "Quảng bá khóa học đã được gỡ",
                "Khóa \"" + p.getCourse().getTitle() + "\": " + reason.trim(), "/instructor/share");
        return toRes(saved, true);
    }

    // ── Công khai (chỉ đọc) ─────────────────────────────────────────────

    /**
     * Kệ "Được đề xuất". Chỉ đọc, đầu vào đã kiểm tra: {@code placement} là enum, {@code categorySlug} theo mẫu cố định, {@code size}
     * kẹp 1–{@value #MAX_FEATURED_SIZE}. Kết quả cache 15 phút (cache `publicCourseSearch`, bị xóa khi duyệt/kết thúc/hủy đợt) để trang
     * chủ không làm tăng tải DB.
     */
    @Cacheable(value = "publicCourseSearch", key = "'featured:' + #placement + ':' + #categorySlug + ':' + #size")
    @Transactional(readOnly = true)
    public PromotionDto.FeaturedRes featured(PromotionPlacement placement, String categorySlug, int size) {
        if (categorySlug != null && !CATEGORY_SLUG.matcher(categorySlug).matches()) {
            throw new InvalidRequestException("Danh mục không hợp lệ");
        }
        int limit = Math.min(Math.max(size, 1), MAX_FEATURED_SIZE);
        var page = PageRequest.of(0, FEATURED_POOL_SIZE);
        LocalDateTime now = LocalDateTime.now();
        List<CoursePromotion> active = categorySlug == null
                ? promotionRepository.findActive(placement, now, page)
                : promotionRepository.findActiveInCategory(placement, categorySlug, now, page);
        // Một khóa chỉ xuất hiện 1 lần dù có nhiều đợt chồng nhau.
        // Giảng viên đang ở bậc công nợ "ngừng bán mới" thì khóa không lên kệ đề xuất.
        java.util.Set<Long> suspended = new java.util.HashSet<>(debtGuard.suspendedInstructorIds());
        List<Course> pool = new ArrayList<>(active.stream().map(CoursePromotion::getCourse).distinct()
                .filter(c -> c.getInstructor() == null || !suspended.contains(c.getInstructor().getId())).toList());
        // Không có "độ ưu tiên" do admin chấm tay (tránh thiên vị): mọi khóa đang chạy đều có cơ hội như nhau, thứ tự xoay
        // vòng theo GIỜ (hạt giống = giờ hiện tại + vị trí) nên không khóa nào chiếm ô đầu mãi mãi.
        Collections.shuffle(pool, new Random(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).hashCode() * 31L + placement.ordinal()));
        List<Course> courses = pool.size() > limit ? List.copyOf(pool.subList(0, limit)) : List.copyOf(pool);
        return new PromotionDto.FeaturedRes(coursePublicService.summariesOf(courses));
    }

    // ── nội bộ ──────────────────────────────────────────────────────────

    /** Khóa đủ điều kiện quảng bá: đã xuất bản, công khai, có ảnh bìa và đủ số bài học tối thiểu. */
    private void assertEligible(Course course) {
        if (course.getStatus() != CourseStatus.PUBLISHED || course.getVisibility() != CourseVisibility.PUBLIC) {
            throw new BusinessRuleViolationException("Chỉ khóa đã xuất bản và công khai mới đăng ký quảng bá được");
        }
        if (course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()) {
            throw new BusinessRuleViolationException("Khóa cần có ảnh bìa trước khi đăng ký quảng bá");
        }
        // Đếm bài thật: cột `courses.total_lessons` là số đã phi chuẩn hóa, không được cập nhật khi giảng viên
        // thêm bài (khóa Unity/AI Agent có 4–6 bài mà cột này = 0 → bị từ chối oan).
        if (lessonRepository.countByChapter_CourseId(course.getId()) < MIN_LESSONS) {
            throw new BusinessRuleViolationException("Khóa cần có ít nhất " + MIN_LESSONS + " bài học trước khi đăng ký quảng bá");
        }
    }

    private CoursePromotion require(Long id) {
        return promotionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("CoursePromotion", id));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private static void requireStatus(CoursePromotion p, Set<PromotionStatus> allowed, String message) {
        if (!allowed.contains(p.getStatus())) {
            throw new BusinessRuleViolationException(message + " (trạng thái hiện tại: " + p.getStatus() + ")");
        }
    }

    private void audit(String actorEmail, String action, CoursePromotion p, String before) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", p.getStatus().name());
        after.put("placement", p.getPlacement().name());
        after.put("startAt", String.valueOf(p.getStartAt()));
        after.put("endAt", String.valueOf(p.getEndAt()));
        auditService.record(actorEmail, action, "course_promotion", String.valueOf(p.getId()), Map.of("status", before), after);
    }

    private PromotionDto.Res toRes(CoursePromotion p, boolean withInstructor) {
        LocalDateTime now = LocalDateTime.now();
        boolean active = p.getStatus() == PromotionStatus.APPROVED && p.getStartAt() != null && p.getEndAt() != null
                && !p.getStartAt().isAfter(now) && p.getEndAt().isAfter(now);
        long ordersDuring = 0;
        long ordersBefore = 0;
        BigDecimal revenueDuring = BigDecimal.ZERO;
        BigDecimal revenueBefore = BigDecimal.ZERO;
        if ((p.getStatus() == PromotionStatus.APPROVED || p.getStatus() == PromotionStatus.ENDED) && p.getStartAt() != null) {
            LocalDateTime end = p.getEndAt() != null && p.getEndAt().isBefore(now) ? p.getEndAt() : now;
            if (end.isAfter(p.getStartAt())) {
                Long courseId = p.getCourse().getId();
                Duration length = Duration.between(p.getStartAt(), end);
                ordersDuring = paymentRepository.countPaidBetween(courseId, p.getStartAt(), end);
                revenueDuring = paymentRepository.sumPaidBetween(courseId, p.getStartAt(), end);
                LocalDateTime beforeStart = p.getStartAt().minus(length);
                ordersBefore = paymentRepository.countPaidBetween(courseId, beforeStart, p.getStartAt());
                revenueBefore = paymentRepository.sumPaidBetween(courseId, beforeStart, p.getStartAt());
            }
        }
        Course c = p.getCourse();
        User requester = p.getRequestedBy();
        return new PromotionDto.Res(p.getId(), c.getId(), c.getTitle(), c.getSlug(), p.getPlacement(), p.getStatus(),
                p.getRequestNote(), p.getStartAt(), p.getEndAt(), p.getAdminNote(), p.getCreatedAt(), active,
                ordersDuring, revenueDuring, ordersBefore, revenueBefore,
                withInstructor ? requester.getFullName() : null, withInstructor ? requester.getEmail() : null);
    }
}
