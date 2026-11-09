package com.lms.wishlist.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.coupon.dto.CouponDto.PriceRes;
import com.lms.coupon.service.CouponService;
import com.lms.enrollment.repository.CourseReviewRepository;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.wishlist.dto.WishlistDto.ItemRes;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Danh sách yêu thích (14/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả
 * gốc. Kiểm tra BR-WISHLIST-01 (chỉ khóa PUBLISHED + chưa sở hữu mới thêm được — KHÁC giỏ
 * hàng, khóa MIỄN PHÍ vẫn thêm được vào wishlist) và BR-WISHLIST-02 (thêm khóa đã có sẵn là
 * thao tác idempotent, không báo lỗi).
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CourseReviewRepository courseReviewRepository;
    @Mock private CouponService couponService;

    @InjectMocks
    private WishlistService wishlistService;

    private User user;
    private User instructor;
    private Course course;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        instructor = new User();
        instructor.setId(99L);
        instructor.setFullName("Co Lan");

        course = new Course();
        course.setId(10L);
        course.setTitle("Unity co ban");
        course.setSlug("unity-co-ban");
        course.setStatus(CourseStatus.PUBLISHED);
        course.setIsFree(false);
        course.setPrice(new BigDecimal("299000"));
        course.setInstructor(instructor);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        lenient().when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> {
            WishlistItem item = inv.getArgument(0);
            item.setId(500L);
            return item;
        });
        // Coupon (15/09/2026, mở rộng) — mặc định KHÔNG có coupon áp dụng cho khóa TRẢ PHÍ
        // (khóa miễn phí không gọi tới hàm này, xem nhánh isFree trong WishlistService.toRes).
        lenient().when(couponService.getDisplayPrice(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            return new PriceRes(c.getPrice(), c.getPrice(), null, null, true);
        });
    }

    @Test
    void addItem_publishedNotOwned_savesAndReturnsItem() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(wishlistItemRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.empty());

        ItemRes result = wishlistService.addItem(EMAIL, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
        assertThat(result.courseTitle()).isEqualTo("Unity co ban");
        assertThat(result.instructorName()).isEqualTo("Co Lan");
        assertThat(result.price()).isEqualByComparingTo("299000");
        ArgumentCaptor<WishlistItem> captor = ArgumentCaptor.forClass(WishlistItem.class);
        verify(wishlistItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCourse()).isEqualTo(course);
    }

    @Test
    void addItem_freeCourse_stillSaves() {
        course.setIsFree(true);
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(wishlistItemRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.empty());

        ItemRes result = wishlistService.addItem(EMAIL, 10L);

        assertThat(result.isFree()).isTrue();
        verify(wishlistItemRepository).save(any());
    }

    @Test
    void addItem_alreadyInWishlist_returnsExistingWithoutCreatingDuplicate() {
        WishlistItem existing = new WishlistItem();
        existing.setId(501L);
        existing.setUser(user);
        existing.setCourse(course);
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(wishlistItemRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(existing));

        ItemRes result = wishlistService.addItem(EMAIL, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void addItem_notPublished_throws() {
        course.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> wishlistService.addItem(EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void addItem_alreadyOwned_throws() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> wishlistService.addItem(EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void getMyWishlist_returnsAllItemsForUser() {
        WishlistItem item = new WishlistItem();
        item.setId(500L);
        item.setUser(user);
        item.setCourse(course);
        when(wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(item));

        List<ItemRes> result = wishlistService.getMyWishlist(EMAIL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseId()).isEqualTo(10L);
    }

    @Test
    void removeItem_deletesByUserAndCourse() {
        wishlistService.removeItem(EMAIL, 10L);

        verify(wishlistItemRepository).deleteByUser_IdAndCourse_Id(1L, 10L);
    }
}
