package com.lms.wishlist.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.coupon.dto.CouponDto.PriceRes;
import com.lms.coupon.service.CouponService;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.wishlist.dto.WishlistDto;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách yêu thích (14/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả
 * gốc của đồ án, cùng tinh thần {@code CartService} (giỏ hàng). Học viên đánh dấu khóa học
 * quan tâm để theo dõi — KHÔNG giới hạn khóa trả phí như giỏ hàng (BR-CART-01), vì đây chỉ
 * là "lưu lại xem sau", không phải bước chuẩn bị thanh toán.
 *
 * <p><b>BR-WISHLIST-01</b> (mở rộng): chỉ thêm được khóa học {@code PUBLISHED} và học viên
 * CHƯA sở hữu — đã sở hữu rồi thì không còn lý do nằm trong danh sách "muốn mua".
 *
 * <p><b>BR-WISHLIST-02</b> (mở rộng, giống BR-CART-02): thêm 1 khóa đã có sẵn trong wishlist
 * là thao tác IDEMPOTENT (trả về đúng dòng đã có, không báo lỗi trùng).
 *
 * <p>Chỗ dành cho tương lai: khi khóa học có logic giảm giá, hàm gửi email cho mọi học viên
 * có khóa đó trong wishlist sẽ được thêm ở đây — chưa làm ở giai đoạn này.
 */
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CouponService couponService;

    @Transactional(readOnly = true)
    public List<WishlistDto.ItemRes> getMyWishlist(String email) {
        User user = requireUser(email);
        return wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toRes)
                .toList();
    }

    @Transactional
    public WishlistDto.ItemRes addItem(String email, Long courseId) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-WISHLIST-01: Chỉ có thể thêm khóa học đã xuất bản vào danh sách yêu thích.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }

        // BR-WISHLIST-02: da co san thi tra ve nguyen dong do, khong tao trung/bao loi.
        WishlistItem existing = wishlistItemRepository.findByUser_IdAndCourse_Id(user.getId(), courseId).orElse(null);
        if (existing != null) {
            return toRes(existing);
        }

        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setCourse(course);
        return toRes(wishlistItemRepository.save(item));
    }

    @Transactional
    public void removeItem(String email, Long courseId) {
        User user = requireUser(email);
        wishlistItemRepository.deleteByUser_IdAndCourse_Id(user.getId(), courseId);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private WishlistDto.ItemRes toRes(WishlistItem item) {
        Course c = item.getCourse();
        long reviewCount = courseReviewRepository.countByCourse_IdAndIsHiddenFalse(c.getId());
        PriceRes price = Boolean.TRUE.equals(c.getIsFree())
                ? new PriceRes(c.getPrice(), c.getPrice(), null, null, true)
                : couponService.getDisplayPrice(c);
        return new WishlistDto.ItemRes(
                c.getId(), c.getTitle(), c.getSlug(), c.getThumbnailUrl(),
                c.getInstructor().getFullName(), c.getPrice(), c.getIsFree(),
                c.getAvgRating(), reviewCount, item.getCreatedAt(),
                price.finalPrice(), price.discountPercent());
    }
}
