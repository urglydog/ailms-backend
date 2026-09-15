package com.lms.coupon.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Category;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CouponScopeType;
import com.lms.common.enums.DiscountType;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.coupon.dto.CouponDto.CreateReq;
import com.lms.coupon.dto.CouponDto.Res;
import com.lms.coupon.entity.Coupon;
import com.lms.coupon.repository.CouponCourseRepository;
import com.lms.coupon.repository.CouponRepository;
import com.lms.coupon.service.CouponService.PricingResult;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Mã giảm giá kiểu Udemy (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57).
 * Kiểm tra BR-COUPON-01 (không cộng dồn, chọn mức giảm cao nhất), BR-COUPON-02 (phạm vi
 * Giảng viên), BR-COUPON-03 (ràng buộc sở hữu), BR-COUPON-04 (autoApply/code).
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final String ADMIN_EMAIL = "admin@lms.local";
    private static final String INSTRUCTOR_EMAIL = "instructor@lms.local";

    @Mock private CouponRepository couponRepository;
    @Mock private CouponCourseRepository couponCourseRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private CouponService couponService;

    private User admin;
    private User instructor;
    private User otherInstructor;
    private Course ownCourse;
    private Course otherCourse;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        instructor = new User();
        instructor.setId(2L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setRole(Role.INSTRUCTOR);

        otherInstructor = new User();
        otherInstructor.setId(3L);
        otherInstructor.setRole(Role.INSTRUCTOR);

        Category category = new Category();
        category.setId(1L);
        category.setName("Cat");

        ownCourse = new Course();
        ownCourse.setId(100L);
        ownCourse.setTitle("Khoa cua toi");
        ownCourse.setPrice(new BigDecimal("200000"));
        ownCourse.setIsFree(false);
        ownCourse.setInstructor(instructor);
        ownCourse.setCategory(category);

        otherCourse = new Course();
        otherCourse.setId(200L);
        otherCourse.setTitle("Khoa nguoi khac");
        otherCourse.setPrice(new BigDecimal("200000"));
        otherCourse.setIsFree(false);
        otherCourse.setInstructor(otherInstructor);
        otherCourse.setCategory(category);

        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByEmail(INSTRUCTOR_EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(courseRepository.findById(100L)).thenReturn(Optional.of(ownCourse));
        lenient().when(courseRepository.findById(200L)).thenReturn(Optional.of(otherCourse));
        lenient().when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            if (c.getId() == null) c.setId(500L);
            return c;
        });
        lenient().when(couponCourseRepository.findByCoupon_Id(anyLong())).thenReturn(List.of());
        lenient().when(paymentRepository.countByCoupon_IdAndStatus(anyLong(), any())).thenReturn(0L);
        lenient().when(paymentRepository.countByCoupon_IdAndUser_IdAndStatus(anyLong(), anyLong(), any())).thenReturn(0L);
    }

    private CreateReq baseReq(CouponScopeType scope, List<Long> courseIds, boolean autoApply, String code) {
        return new CreateReq(
                code, autoApply, DiscountType.PERCENTAGE, BigDecimal.TEN, scope, courseIds,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), null, null);
    }

    // ==================== CRUD / ownership ====================

    @Test
    void create_instructorAllCourses_succeeds() {
        Res result = couponService.create(INSTRUCTOR_EMAIL, baseReq(CouponScopeType.ALL_COURSES, null, true, null));

        assertThat(result.scopeType()).isEqualTo(CouponScopeType.ALL_COURSES);
        assertThat(result.autoApply()).isTrue();
        assertThat(result.code()).isNull();
    }

    @Test
    void create_instructorSpecificCourseNotOwned_throwsBrCoupon03() {
        assertThatThrownBy(() -> couponService.create(
                INSTRUCTOR_EMAIL, baseReq(CouponScopeType.SPECIFIC_COURSES, List.of(200L), true, null)))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessageContaining("BR-COUPON-03");
    }

    @Test
    void create_instructorSpecificCourseOwned_succeeds() {
        Res result = couponService.create(
                INSTRUCTOR_EMAIL, baseReq(CouponScopeType.SPECIFIC_COURSES, List.of(100L), true, null));

        assertThat(result.scopeType()).isEqualTo(CouponScopeType.SPECIFIC_COURSES);
    }

    @Test
    void create_adminSpecificCourseAnyOwner_succeeds() {
        Res result = couponService.create(
                ADMIN_EMAIL, baseReq(CouponScopeType.SPECIFIC_COURSES, List.of(200L), true, null));

        assertThat(result.scopeType()).isEqualTo(CouponScopeType.SPECIFIC_COURSES);
    }

    @Test
    void create_notAutoApply_withoutCode_throws() {
        assertThatThrownBy(() -> couponService.create(
                ADMIN_EMAIL, baseReq(CouponScopeType.ALL_COURSES, null, false, "  ")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_autoApplyTrue_ignoresProvidedCode() {
        Res result = couponService.create(
                ADMIN_EMAIL, baseReq(CouponScopeType.ALL_COURSES, null, true, "SHOULDBEIGNORED"));

        assertThat(result.code()).isNull();
    }

    @Test
    void create_percentageOver100_throws() {
        CreateReq req = new CreateReq(
                null, true, DiscountType.PERCENTAGE, new BigDecimal("150"), CouponScopeType.ALL_COURSES, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), null, null);

        assertThatThrownBy(() -> couponService.create(ADMIN_EMAIL, req))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_startAfterEnd_throws() {
        CreateReq req = new CreateReq(
                null, true, DiscountType.PERCENTAGE, BigDecimal.TEN, CouponScopeType.ALL_COURSES, null,
                LocalDateTime.now().plusDays(5), LocalDateTime.now(), null, null);

        assertThatThrownBy(() -> couponService.create(ADMIN_EMAIL, req))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_specificCourseScope_withoutCourseIds_throws() {
        assertThatThrownBy(() -> couponService.create(
                ADMIN_EMAIL, baseReq(CouponScopeType.SPECIFIC_COURSES, List.of(), true, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ==================== resolveBestPrice (BR-COUPON-01/02/04) ====================

    private Coupon activeAutoApplyCoupon(User creator, CouponScopeType scope, DiscountType type, BigDecimal value) {
        Coupon coupon = new Coupon();
        coupon.setId(900L);
        coupon.setAutoApply(true);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setScopeType(scope);
        coupon.setCreatedBy(creator);
        coupon.setStartAt(LocalDateTime.now().minusDays(1));
        coupon.setEndAt(LocalDateTime.now().plusDays(1));
        coupon.setIsActive(true);
        return coupon;
    }

    @Test
    void resolveBestPrice_noCoupons_returnsOriginalPrice() {
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of());

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, null);

        assertThat(result.finalPrice()).isEqualByComparingTo(ownCourse.getPrice());
        assertThat(result.appliedCoupon()).isNull();
    }

    @Test
    void resolveBestPrice_autoApplyPercentageCoupon_appliesDiscount() {
        Coupon coupon = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, new BigDecimal("20"));
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(coupon));

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, null);

        assertThat(result.finalPrice()).isEqualByComparingTo("160000.00");
        assertThat(result.appliedCoupon()).isEqualTo(coupon);
    }

    @Test
    void resolveBestPrice_multipleCoupons_choosesLowestPrice_notStacked() {
        Coupon small = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, new BigDecimal("10"));
        small.setId(901L);
        Coupon big = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, new BigDecimal("30"));
        big.setId(902L);
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(small, big));

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, null);

        // 30% giam (140000) tot hon 10% (180000) — KHONG cong don thanh 40%.
        assertThat(result.finalPrice()).isEqualByComparingTo("140000.00");
        assertThat(result.appliedCoupon()).isEqualTo(big);
    }

    @Test
    void resolveBestPrice_instructorAllCoursesCoupon_onlyMatchesOwnCourses() {
        Coupon instructorCoupon = activeAutoApplyCoupon(instructor, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, BigDecimal.TEN);
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(instructorCoupon));

        PricingResult onOwnCourse = couponService.resolveBestPrice(ownCourse, null, null);
        PricingResult onOtherCourse = couponService.resolveBestPrice(otherCourse, null, null);

        assertThat(onOwnCourse.appliedCoupon()).isEqualTo(instructorCoupon);
        assertThat(onOtherCourse.appliedCoupon()).isNull();
        assertThat(onOtherCourse.finalPrice()).isEqualByComparingTo(otherCourse.getPrice());
    }

    @Test
    void resolveBestPrice_specificCourseCoupon_onlyMatchesListedCourse() {
        Coupon coupon = activeAutoApplyCoupon(admin, CouponScopeType.SPECIFIC_COURSES, DiscountType.FIXED_AMOUNT, new BigDecimal("50000"));
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(coupon));
        when(couponCourseRepository.existsByCoupon_IdAndCourse_Id(900L, 100L)).thenReturn(true);
        when(couponCourseRepository.existsByCoupon_IdAndCourse_Id(900L, 200L)).thenReturn(false);

        PricingResult matched = couponService.resolveBestPrice(ownCourse, null, null);
        PricingResult notMatched = couponService.resolveBestPrice(otherCourse, null, null);

        assertThat(matched.finalPrice()).isEqualByComparingTo("150000.00");
        assertThat(notMatched.appliedCoupon()).isNull();
    }

    @Test
    void resolveBestPrice_expiredCoupon_notApplied() {
        Coupon expired = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, BigDecimal.TEN);
        expired.setEndAt(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(expired));

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, null);

        assertThat(result.appliedCoupon()).isNull();
    }

    @Test
    void resolveBestPrice_maxUsageCountReached_notApplied() {
        Coupon coupon = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, BigDecimal.TEN);
        coupon.setMaxUsageCount(5);
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of(coupon));
        when(paymentRepository.countByCoupon_IdAndStatus(900L, PaymentStatus.PAID)).thenReturn(5L);

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, null);

        assertThat(result.appliedCoupon()).isNull();
    }

    @Test
    void resolveBestPrice_enteredCodeNotApplicableToCourse_flagsInvalid() {
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of());
        Coupon codeCoupon = activeAutoApplyCoupon(admin, CouponScopeType.SPECIFIC_COURSES, DiscountType.PERCENTAGE, BigDecimal.TEN);
        codeCoupon.setAutoApply(false);
        codeCoupon.setCode("SALE10");
        when(couponRepository.findByCodeIgnoreCaseAndIsActiveTrue("SALE10")).thenReturn(Optional.of(codeCoupon));
        when(couponCourseRepository.existsByCoupon_IdAndCourse_Id(900L, 100L)).thenReturn(false);

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, "SALE10");

        assertThat(result.enteredCodeValid()).isFalse();
        assertThat(result.appliedCoupon()).isNull();
        assertThat(result.finalPrice()).isEqualByComparingTo(ownCourse.getPrice());
    }

    @Test
    void resolveBestPrice_enteredCodeValid_appliesDiscount() {
        when(couponRepository.findByAutoApplyTrueAndIsActiveTrue()).thenReturn(List.of());
        Coupon codeCoupon = activeAutoApplyCoupon(admin, CouponScopeType.ALL_COURSES, DiscountType.PERCENTAGE, new BigDecimal("15"));
        codeCoupon.setAutoApply(false);
        codeCoupon.setCode("SALE15");
        when(couponRepository.findByCodeIgnoreCaseAndIsActiveTrue("sale15")).thenReturn(Optional.of(codeCoupon));

        PricingResult result = couponService.resolveBestPrice(ownCourse, null, "sale15");

        assertThat(result.enteredCodeValid()).isTrue();
        assertThat(result.appliedCoupon()).isEqualTo(codeCoupon);
        assertThat(result.finalPrice()).isEqualByComparingTo("170000.00");
    }
}
