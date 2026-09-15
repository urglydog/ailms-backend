package com.lms.coupon.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CouponScopeType;
import com.lms.common.enums.DiscountType;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.coupon.dto.CouponDto.CourseRef;
import com.lms.coupon.dto.CouponDto.CreateReq;
import com.lms.coupon.dto.CouponDto.PriceRes;
import com.lms.coupon.dto.CouponDto.Res;
import com.lms.coupon.dto.CouponDto.UpdateReq;
import com.lms.coupon.entity.Coupon;
import com.lms.coupon.entity.CouponCourse;
import com.lms.coupon.repository.CouponCourseRepository;
import com.lms.coupon.repository.CouponRepository;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mã giảm giá kiểu Udemy (15/09/2026, mở rộng ngoài đặc tả gốc — UC55/UC56/UC57). Xem
 * doc/01_ThietKeLai_GiangVien_ThanhToan_Coupon.md mục 3.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponCourseRepository couponCourseRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PaymentRepository paymentRepository;

    /** Kết quả tính giá cho 1 khóa học — dùng nội bộ, không phải DTO trả về API trực tiếp. */
    public record PricingResult(
            BigDecimal originalPrice,
            BigDecimal finalPrice,
            Coupon appliedCoupon,
            boolean enteredCodeValid
    ) {}

    // ==================== CRUD ====================

    @Transactional
    public Res create(String email, CreateReq req) {
        User creator = requireUser(email);
        validateDiscountValue(req.discountType(), req.discountValue());
        if (!req.startAt().isBefore(req.endAt())) {
            throw new InvalidRequestException("Ngày bắt đầu phải trước ngày kết thúc.");
        }

        String normalizedCode = normalizeCode(req.autoApply(), req.code());
        if (normalizedCode != null && couponRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleViolationException("Mã giảm giá \"" + normalizedCode + "\" đã tồn tại.");
        }

        List<Course> courses = resolveAndAuthorizeCourses(creator, req.scopeType(), req.courseIds());

        Coupon coupon = new Coupon();
        coupon.setCode(normalizedCode);
        coupon.setAutoApply(req.autoApply());
        coupon.setDiscountType(req.discountType());
        coupon.setDiscountValue(req.discountValue());
        coupon.setScopeType(req.scopeType());
        coupon.setCreatedBy(creator);
        coupon.setStartAt(req.startAt());
        coupon.setEndAt(req.endAt());
        coupon.setMaxUsageCount(req.maxUsageCount());
        coupon.setMaxUsagePerUser(req.maxUsagePerUser());
        coupon.setIsActive(true);
        coupon = couponRepository.save(coupon);

        saveCouponCourses(coupon, courses);
        return toRes(coupon);
    }

    @Transactional
    public Res update(String email, Long id, UpdateReq req) {
        User requester = requireUser(email);
        Coupon coupon = loadOwnedCoupon(id, requester);

        validateDiscountValue(req.discountType(), req.discountValue());
        if (!req.startAt().isBefore(req.endAt())) {
            throw new InvalidRequestException("Ngày bắt đầu phải trước ngày kết thúc.");
        }

        String normalizedCode = normalizeCode(req.autoApply(), req.code());
        if (normalizedCode != null
                && !normalizedCode.equalsIgnoreCase(coupon.getCode() == null ? "" : coupon.getCode())
                && couponRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleViolationException("Mã giảm giá \"" + normalizedCode + "\" đã tồn tại.");
        }

        List<Course> courses = resolveAndAuthorizeCourses(
                coupon.getCreatedBy().getRole() == Role.ADMIN ? requester : coupon.getCreatedBy(),
                req.scopeType(), req.courseIds());

        coupon.setCode(normalizedCode);
        coupon.setAutoApply(req.autoApply());
        coupon.setDiscountType(req.discountType());
        coupon.setDiscountValue(req.discountValue());
        coupon.setScopeType(req.scopeType());
        coupon.setStartAt(req.startAt());
        coupon.setEndAt(req.endAt());
        coupon.setMaxUsageCount(req.maxUsageCount());
        coupon.setMaxUsagePerUser(req.maxUsagePerUser());
        coupon.setIsActive(req.isActive());
        coupon = couponRepository.save(coupon);

        couponCourseRepository.deleteByCoupon_Id(coupon.getId());
        saveCouponCourses(coupon, courses);
        return toRes(coupon);
    }

    @Transactional
    public void delete(String email, Long id) {
        User requester = requireUser(email);
        Coupon coupon = loadOwnedCoupon(id, requester);
        couponRepository.delete(coupon);
    }

    /** Admin thấy TOÀN BỘ coupon hệ thống; Instructor chỉ thấy coupon CHÍNH MÌNH tạo (BR-COUPON-02/03). */
    @Transactional(readOnly = true)
    public List<Res> listMine(String email) {
        User requester = requireUser(email);
        List<Coupon> coupons = requester.getRole() == Role.ADMIN
                ? couponRepository.findAll()
                : couponRepository.findByCreatedBy_Id(requester.getId());
        return coupons.stream().map(this::toRes).toList();
    }

    // ==================== Tính giá (BR-COUPON-01/04/05) ====================

    /** BR-COUPON-04 — chỉ xét coupon {@code autoApply=true}, dùng cho thẻ khóa học/trang chi tiết/giỏ hàng/wishlist. */
    @Transactional(readOnly = true)
    public PriceRes getDisplayPrice(Course course) {
        PricingResult result = resolveBestPrice(course, null, null);
        return toPriceRes(result);
    }

    /** Dùng lúc thanh toán — xét CẢ coupon autoApply LẪN mã học viên tự nhập (BR-COUPON-01: không cộng dồn, chọn mức giảm cao nhất). */
    @Transactional(readOnly = true)
    public PriceRes previewPrice(Course course, User user, String enteredCode) {
        PricingResult result = resolveBestPrice(course, user, enteredCode);
        return toPriceRes(result);
    }

    /** Xem trước giá ở giỏ hàng/thanh toán khi học viên gõ mã — không tạo giao dịch nào. */
    @Transactional(readOnly = true)
    public PriceRes previewPrice(String email, Long courseId, String enteredCode) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        return previewPrice(course, user, enteredCode);
    }

    /**
     * BR-COUPON-01/05 — với mỗi khóa học, gom mọi coupon {@code autoApply=true} ĐANG hợp lệ
     * (còn hạn, đúng phạm vi, chưa hết hạn mức) cộng thêm coupon mã {@code enteredCode} NẾU
     * hợp lệ cho đúng khóa này, rồi chọn ra coupon cho GIÁ THẤP NHẤT — không cộng dồn nhiều
     * lớp giảm giá dù có bao nhiêu coupon đủ điều kiện.
     */
    @Transactional(readOnly = true)
    public PricingResult resolveBestPrice(Course course, User user, String enteredCode) {
        BigDecimal originalPrice = course.getPrice();
        LocalDateTime now = LocalDateTime.now();

        List<Coupon> candidates = new ArrayList<>();
        for (Coupon coupon : couponRepository.findByAutoApplyTrueAndIsActiveTrue()) {
            if (isCouponUsable(coupon, course, user, now)) {
                candidates.add(coupon);
            }
        }

        boolean enteredCodeValid = true;
        if (enteredCode != null && !enteredCode.isBlank()) {
            Coupon entered = couponRepository.findByCodeIgnoreCaseAndIsActiveTrue(enteredCode.trim()).orElse(null);
            if (entered != null && isCouponUsable(entered, course, user, now)) {
                candidates.add(entered);
            } else {
                enteredCodeValid = false;
            }
        }

        if (candidates.isEmpty()) {
            return new PricingResult(originalPrice, originalPrice, null, enteredCodeValid);
        }

        Coupon best = candidates.stream()
                .min(Comparator.comparing(c -> computeDiscountedPrice(c, originalPrice)))
                .orElseThrow();
        BigDecimal finalPrice = computeDiscountedPrice(best, originalPrice);
        return new PricingResult(originalPrice, finalPrice, best, enteredCodeValid);
    }

    private boolean isCouponUsable(Coupon coupon, Course course, User user, LocalDateTime now) {
        if (!Boolean.TRUE.equals(coupon.getIsActive())) return false;
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) return false;
        if (!scopeMatches(coupon, course)) return false;
        if (coupon.getMaxUsageCount() != null) {
            long used = paymentRepository.countByCoupon_IdAndStatus(coupon.getId(), PaymentStatus.PAID);
            if (used >= coupon.getMaxUsageCount()) return false;
        }
        if (user != null && coupon.getMaxUsagePerUser() != null) {
            long usedByUser = paymentRepository.countByCoupon_IdAndUser_IdAndStatus(coupon.getId(), user.getId(), PaymentStatus.PAID);
            if (usedByUser >= coupon.getMaxUsagePerUser()) return false;
        }
        return true;
    }

    /** BR-COUPON-02 — ALL_COURSES của Instructor chỉ khớp khóa CỦA CHÍNH họ, của Admin khớp mọi khóa. */
    private boolean scopeMatches(Coupon coupon, Course course) {
        return switch (coupon.getScopeType()) {
            case ALL_COURSES -> coupon.getCreatedBy().getRole() == Role.ADMIN
                    || coupon.getCreatedBy().getId().equals(course.getInstructor().getId());
            case SPECIFIC_COURSES, SINGLE_COURSE ->
                    couponCourseRepository.existsByCoupon_IdAndCourse_Id(coupon.getId(), course.getId());
        };
    }

    private BigDecimal computeDiscountedPrice(Coupon coupon, BigDecimal originalPrice) {
        BigDecimal discounted = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? originalPrice.multiply(BigDecimal.ONE.subtract(
                        coupon.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                : originalPrice.subtract(coupon.getDiscountValue());
        return discounted.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== helpers ====================

    private void validateDiscountValue(DiscountType type, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Giá trị giảm giá phải lớn hơn 0.");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidRequestException("Giảm theo % không được vượt quá 100.");
        }
    }

    /** {@code autoApply=true} luôn lưu {@code code=NULL}; {@code autoApply=false} bắt buộc có mã (BR-COUPON-04). */
    private String normalizeCode(Boolean autoApply, String code) {
        if (Boolean.TRUE.equals(autoApply)) {
            return null;
        }
        if (code == null || code.isBlank()) {
            throw new InvalidRequestException("Coupon cần nhập mã (không tự động) phải có mã giảm giá.");
        }
        return code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** BR-COUPON-03 — Instructor chỉ gán được coupon vào khóa học CỦA CHÍNH MÌNH; Admin gán tuỳ ý. */
    private List<Course> resolveAndAuthorizeCourses(User owner, CouponScopeType scopeType, List<Long> courseIds) {
        if (scopeType == CouponScopeType.ALL_COURSES) {
            return List.of();
        }
        if (courseIds == null || courseIds.isEmpty()) {
            throw new InvalidRequestException("Cần chọn ít nhất 1 khóa học cho phạm vi này.");
        }
        if (scopeType == CouponScopeType.SINGLE_COURSE && courseIds.size() != 1) {
            throw new InvalidRequestException("Phạm vi \"1 khóa học\" chỉ được chọn đúng 1 khóa.");
        }

        List<Course> courses = courseIds.stream().distinct()
                .map(courseId -> courseRepository.findById(courseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Course", courseId)))
                .toList();

        if (owner.getRole() != Role.ADMIN) {
            for (Course course : courses) {
                if (!course.getInstructor().getId().equals(owner.getId())) {
                    throw new AccessDeniedDomainException(
                            "BR-COUPON-03: Bạn chỉ được tạo coupon cho khóa học của chính mình (\"" + course.getTitle() + "\" không thuộc sở hữu của bạn).");
                }
            }
        }
        return courses;
    }

    private void saveCouponCourses(Coupon coupon, List<Course> courses) {
        for (Course course : courses) {
            CouponCourse cc = new CouponCourse();
            cc.setCoupon(coupon);
            cc.setCourse(course);
            couponCourseRepository.save(cc);
        }
    }

    private Coupon loadOwnedCoupon(Long id, User requester) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id));
        if (requester.getRole() != Role.ADMIN && !coupon.getCreatedBy().getId().equals(requester.getId())) {
            throw new AccessDeniedDomainException("Bạn không có quyền thao tác trên coupon này.");
        }
        return coupon;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private Res toRes(Coupon coupon) {
        List<CourseRef> courseRefs = coupon.getScopeType() == CouponScopeType.ALL_COURSES
                ? List.of()
                : couponCourseRepository.findByCoupon_Id(coupon.getId()).stream()
                        .map(cc -> new CourseRef(cc.getCourse().getId(), cc.getCourse().getTitle()))
                        .toList();
        long usageCount = paymentRepository.countByCoupon_IdAndStatus(coupon.getId(), PaymentStatus.PAID);

        return new Res(
                coupon.getId(), coupon.getCode(), coupon.getAutoApply(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getScopeType(), courseRefs,
                coupon.getCreatedBy().getFullName(), coupon.getStartAt(), coupon.getEndAt(),
                coupon.getMaxUsageCount(), coupon.getMaxUsagePerUser(), coupon.getIsActive(), usageCount
        );
    }

    private PriceRes toPriceRes(PricingResult result) {
        Integer discountPercent = null;
        if (result.appliedCoupon() != null && result.originalPrice().compareTo(BigDecimal.ZERO) > 0) {
            discountPercent = result.originalPrice().subtract(result.finalPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(result.originalPrice(), 0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return new PriceRes(
                result.originalPrice(), result.finalPrice(), discountPercent,
                result.appliedCoupon() != null ? result.appliedCoupon().getCode() : null,
                result.enteredCodeValid()
        );
    }
}
