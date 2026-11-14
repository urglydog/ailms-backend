package com.lms.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lms.common.service.NotificationService;
import com.lms.common.util.CacheEvictionHelper;
import com.lms.finance.service.FinanceAuditService;
import com.lms.payment.repository.PaymentRepository;
import com.lms.promotion.dto.PromotionDto;
import com.lms.promotion.entity.CoursePromotion;
import com.lms.promotion.repository.CoursePromotionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    private static final String EMAIL = "gv@lms.local";
    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private CoursePromotionRepository promotionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CoursePublicService coursePublicService;
    @Mock private CacheEvictionHelper cacheEvictionHelper;
    @Mock private NotificationService notificationService;
    @Mock private FinanceAuditService auditService;
    @Mock private LessonRepository lessonRepository;

    @InjectMocks
    private PromotionService service;

    private User instructor;
    private User admin;
    private Course course;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(7L);
        instructor.setEmail(EMAIL);
        instructor.setFullName("Giảng Viên");
        instructor.setRole(Role.INSTRUCTOR);
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        course = new Course();
        course.setId(10L);
        course.setTitle("Unity co ban");
        course.setSlug("unity");
        course.setStatus(CourseStatus.PUBLISHED);
        course.setVisibility(CourseVisibility.PUBLIC);
        course.setThumbnailUrl("https://cdn/x.png");
        // cột total_lessons cố ý để 0 như dữ liệu thật; điều kiện phải dựa vào số bài đếm được
        lenient().when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(12L);
        course.setInstructor(instructor);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(promotionRepository.save(any(CoursePromotion.class))).thenAnswer(inv -> {
            CoursePromotion p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(300L);
            }
            return p;
        });
        lenient().when(paymentRepository.countPaidBetween(any(), any(), any())).thenReturn(0L);
        lenient().when(paymentRepository.sumPaidBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    private PromotionDto.CreateReq createReq() {
        return new PromotionDto.CreateReq(10L, PromotionPlacement.HOME_FEATURED, "  khóa mới  ");
    }

    private CoursePromotion newPromotion(PromotionStatus status) {
        CoursePromotion p = new CoursePromotion();
        p.setId(300L);
        p.setCourse(course);
        p.setPlacement(PromotionPlacement.HOME_FEATURED);
        p.setStatus(status);
        p.setRequestedBy(instructor);
        return p;
    }

    /** Như {@link #newPromotion} nhưng còn đăng ký sẵn để service tìm theo id. */
    private CoursePromotion promotion(PromotionStatus status) {
        CoursePromotion p = newPromotion(status);
        when(promotionRepository.findById(300L)).thenReturn(Optional.of(p));
        return p;
    }

    // ── đăng ký ─────────────────────────────────────────────────────────

    @Test
    void request_eligibleCourseByItsOwner_createsRequestedAndNotifiesAdmins() {
        PromotionDto.Res res = service.request(EMAIL, createReq());

        assertThat(res.status()).isEqualTo(PromotionStatus.REQUESTED);
        assertThat(res.requestNote()).isEqualTo("khóa mới");
        assertThat(res.active()).isFalse();
        verify(notificationService).notify(eq(1L), eq("PROMOTION_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void request_forSomeoneElsesCourse_isForbidden() {
        User other = new User();
        other.setId(99L);
        course.setInstructor(other);

        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(AccessDeniedDomainException.class);
        verify(promotionRepository, never()).save(any(CoursePromotion.class));
    }

    @Test
    void request_courseNotEligible_isRejectedWithAClearReason() {
        course.setStatus(CourseStatus.DRAFT);
        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(BusinessRuleViolationException.class);

        course.setStatus(CourseStatus.PUBLISHED);
        course.setVisibility(CourseVisibility.PRIVATE_INVITE);
        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(BusinessRuleViolationException.class);

        course.setVisibility(CourseVisibility.PUBLIC);
        course.setThumbnailUrl(" ");
        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(BusinessRuleViolationException.class);

        course.setThumbnailUrl("https://cdn/x.png");
        when(lessonRepository.countByChapter_CourseId(10L)).thenReturn(2L);
        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(BusinessRuleViolationException.class);

        verify(promotionRepository, never()).save(any(CoursePromotion.class));
    }

    @Test
    void request_whenAnOpenRequestAlreadyExistsForTheSamePlacement_isRejected() {
        when(promotionRepository.existsByCourse_IdAndPlacementAndStatusIn(eq(10L), eq(PromotionPlacement.HOME_FEATURED), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.request(EMAIL, createReq())).isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── admin ───────────────────────────────────────────────────────────

    @Test
    void approve_setsWindow_evictsCacheAuditsAndNotifiesTheInstructor() {
        CoursePromotion p = promotion(PromotionStatus.REQUESTED);
        LocalDateTime end = LocalDateTime.now().plusDays(14);

        PromotionDto.Res res = service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(null, end, " ghi chú "));

        assertThat(p.getStatus()).isEqualTo(PromotionStatus.APPROVED);
        assertThat(p.getStartAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(p.getEndAt()).isEqualTo(end);
        assertThat(p.getAdminNote()).isEqualTo("ghi chú");
        assertThat(p.getReviewedBy()).isSameAs(admin);
        assertThat(res.active()).isTrue();
        verify(cacheEvictionHelper).evictCourseCacheAfterCommit("unity");
        verify(auditService).record(eq(ADMIN_EMAIL), eq("PROMOTION_APPROVED"), eq("course_promotion"), eq("300"), any(), any());
        verify(notificationService).notify(eq(7L), eq("PROMOTION_APPROVED"), anyString(), anyString(), anyString());
    }

    @Test
    void approve_rejectsBadWindows() {
        promotion(PromotionStatus.REQUESTED);
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(now.plusDays(2), now.plusDays(1), null)))
                .isInstanceOf(InvalidRequestException.class); // kết thúc trước bắt đầu
        assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(now.minusDays(5), now.minusDays(1), null)))
                .isInstanceOf(InvalidRequestException.class); // đã ở quá khứ
        assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(null, now.plusDays(91), null)))
                .isInstanceOf(InvalidRequestException.class); // quá 90 ngày
        verify(cacheEvictionHelper, never()).evictCourseCacheAfterCommit(any());
    }

    @Test
    void approve_onlyFromRequested_andOnlyIfCourseStillEligible() {
        CoursePromotion p = promotion(PromotionStatus.APPROVED);
        LocalDateTime end = LocalDateTime.now().plusDays(7);
        assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(null, end, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        p.setStatus(PromotionStatus.REQUESTED);
        course.setStatus(CourseStatus.DRAFT); // giảng viên gỡ xuất bản sau khi đăng ký
        assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 300L, new PromotionDto.ApproveReq(null, end, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reject_requiresReason_andRecordsIt() {
        CoursePromotion p = promotion(PromotionStatus.REQUESTED);
        assertThatThrownBy(() -> service.reject(ADMIN_EMAIL, 300L, " ")).isInstanceOf(InvalidRequestException.class);

        service.reject(ADMIN_EMAIL, 300L, " Chưa đạt chất lượng ");

        assertThat(p.getStatus()).isEqualTo(PromotionStatus.REJECTED);
        assertThat(p.getAdminNote()).isEqualTo("Chưa đạt chất lượng");
        verify(notificationService).notify(eq(7L), eq("PROMOTION_REJECTED"), anyString(), anyString(), anyString());
    }

    @Test
    void end_stopsAnApprovedPromotionNowAndEvictsCache() {
        CoursePromotion p = promotion(PromotionStatus.APPROVED);
        p.setStartAt(LocalDateTime.now().minusDays(2));
        p.setEndAt(LocalDateTime.now().plusDays(5));

        assertThatThrownBy(() -> service.end(ADMIN_EMAIL, 300L, " ")).isInstanceOf(InvalidRequestException.class);

        service.end(ADMIN_EMAIL, 300L, " Vi phạm bản quyền ");

        assertThat(p.getStatus()).isEqualTo(PromotionStatus.ENDED);
        assertThat(p.getAdminNote()).isEqualTo("Vi phạm bản quyền");
        verify(notificationService).notify(eq(7L), eq("PROMOTION_ENDED"), anyString(), anyString(), anyString());
        assertThat(p.getEndAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(cacheEvictionHelper).evictCourseCacheAfterCommit("unity");

        p.setStatus(PromotionStatus.REQUESTED);
        assertThatThrownBy(() -> service.end(ADMIN_EMAIL, 300L, "x")).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void cancel_byOwnerOnlyAndOnlyWhileOpen() {
        CoursePromotion p = promotion(PromotionStatus.REQUESTED);
        assertThatThrownBy(() -> service.cancel("khac@lms.local", 300L)).isInstanceOf(AccessDeniedDomainException.class);

        service.cancel(EMAIL, 300L);
        assertThat(p.getStatus()).isEqualTo(PromotionStatus.CANCELLED);
        verify(cacheEvictionHelper).evictCourseCacheAfterCommit("unity");

        assertThatThrownBy(() -> service.cancel(EMAIL, 300L)).isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── thống kê ────────────────────────────────────────────────────────

    @Test
    void listForAdmin_comparesTheRunWithAnEqualLengthWindowRightBefore() {
        CoursePromotion p = newPromotion(PromotionStatus.ENDED);
        LocalDateTime base = LocalDateTime.now(); // cùng 1 mốc: hai lần now() khác nhau vài micro giây làm lệch độ dài khoảng so sánh
        LocalDateTime start = base.minusDays(10);
        LocalDateTime end = base.minusDays(6); // chạy đúng 4 ngày
        p.setStartAt(start);
        p.setEndAt(end);
        when(promotionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(p));
        when(paymentRepository.countPaidBetween(10L, start, end)).thenReturn(9L);
        when(paymentRepository.sumPaidBetween(10L, start, end)).thenReturn(new BigDecimal("900000"));
        when(paymentRepository.countPaidBetween(10L, start.minusDays(4), start)).thenReturn(3L);
        when(paymentRepository.sumPaidBetween(10L, start.minusDays(4), start)).thenReturn(new BigDecimal("300000"));

        PromotionDto.Res res = service.listForAdmin(null).get(0);

        assertThat(res.ordersDuring()).isEqualTo(9);
        assertThat(res.revenueDuring()).isEqualByComparingTo("900000");
        assertThat(res.ordersBefore()).isEqualTo(3);
        assertThat(res.revenueBefore()).isEqualByComparingTo("300000");
        assertThat(res.active()).isFalse();
        assertThat(res.instructorEmail()).isEqualTo(EMAIL); // admin thấy giảng viên
    }

    @Test
    void listMine_hidesInstructorContactAndSkipsStatsForUnapproved() {
        CoursePromotion p = newPromotion(PromotionStatus.REQUESTED);
        when(promotionRepository.findByRequestedBy_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(p));

        PromotionDto.Res res = service.listMine(EMAIL).get(0);

        assertThat(res.instructorEmail()).isNull();
        assertThat(res.ordersDuring()).isZero();
        verify(paymentRepository, never()).countPaidBetween(any(), any(), any());
    }

    // ── kệ công khai ────────────────────────────────────────────────────

    @Test
    void featured_rejectsMalformedCategorySlugBeforeTouchingTheDatabase() {
        for (String bad : new String[]{"'; DROP TABLE courses;--", "Có Dấu", "UPPER", "-bat-dau-bang-gach", "a".repeat(101), "a b"}) {
            assertThatThrownBy(() -> service.featured(PromotionPlacement.HOME_FEATURED, bad, 8)).isInstanceOf(InvalidRequestException.class);
        }
        verify(promotionRepository, never()).findActive(any(), any(), any());
        verify(promotionRepository, never()).findActiveInCategory(any(), any(), any(), any());
    }

    @Test
    void featured_clampsSize_usesCategoryQueryOnlyWhenGiven_andDeduplicatesCourses() {
        CoursePromotion a = new CoursePromotion();
        a.setCourse(course);
        CoursePromotion b = new CoursePromotion();
        b.setCourse(course); // cùng khóa có 2 đợt chồng nhau
        when(promotionRepository.findActive(eq(PromotionPlacement.HOME_FEATURED), any(), any(Pageable.class))).thenReturn(List.of(a, b));
        when(promotionRepository.findActiveInCategory(eq(PromotionPlacement.CATEGORY_FEATURED), eq("lap-trinh"), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(coursePublicService.summariesOf(any())).thenReturn(List.of());

        service.featured(PromotionPlacement.HOME_FEATURED, null, 9999);
        service.featured(PromotionPlacement.CATEGORY_FEATURED, "lap-trinh", 0);

        ArgumentCaptor<Pageable> homePage = ArgumentCaptor.forClass(Pageable.class);
        verify(promotionRepository).findActive(eq(PromotionPlacement.HOME_FEATURED), any(), homePage.capture());
        assertThat(homePage.getValue().getPageSize()).isEqualTo(PromotionService.FEATURED_POOL_SIZE);
        ArgumentCaptor<Pageable> catPage = ArgumentCaptor.forClass(Pageable.class);
        verify(promotionRepository).findActiveInCategory(eq(PromotionPlacement.CATEGORY_FEATURED), eq("lap-trinh"), any(), catPage.capture());
        assertThat(catPage.getValue().getPageSize()).isEqualTo(PromotionService.FEATURED_POOL_SIZE);
        ArgumentCaptor<List<Course>> courses = ArgumentCaptor.forClass(List.class);
        verify(coursePublicService, org.mockito.Mockito.times(2)).summariesOf(courses.capture());
        assertThat(courses.getAllValues().get(0)).containsExactly(course); // 2 đợt cùng 1 khóa → xuất hiện 1 lần
    }

    @Test
    void featured_cutsThePoolToSize_withoutAnyAdminPriority() {
        List<CoursePromotion> five = new java.util.ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            Course c = new Course();
            c.setId(i);
            CoursePromotion cp = new CoursePromotion();
            cp.setCourse(c);
            five.add(cp);
        }
        when(promotionRepository.findActive(eq(PromotionPlacement.HOME_FEATURED), any(), any(Pageable.class))).thenReturn(five);
        when(coursePublicService.summariesOf(any())).thenReturn(List.of());

        service.featured(PromotionPlacement.HOME_FEATURED, null, 3);

        ArgumentCaptor<List<Course>> courses = ArgumentCaptor.forClass(List.class);
        verify(coursePublicService).summariesOf(courses.capture());
        assertThat(courses.getValue()).hasSize(3).doesNotHaveDuplicates();
    }
}
