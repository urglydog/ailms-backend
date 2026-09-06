package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.dto.CartDto.ItemRes;
import com.lms.payment.entity.CartItem;
import com.lms.payment.repository.CartItemRepository;
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
 * Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc.
 * Kiểm tra BR-CART-01 (chỉ khóa PUBLISHED + trả phí + chưa sở hữu mới thêm được vào giỏ)
 * và BR-CART-02 (thêm khóa đã có sẵn trong giỏ là thao tác idempotent, không báo lỗi).
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private CartService cartService;

    private User user;
    private User instructor;
    private Course paidCourse;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        instructor = new User();
        instructor.setId(99L);
        instructor.setFullName("Co Lan");

        paidCourse = new Course();
        paidCourse.setId(10L);
        paidCourse.setTitle("Unity co ban");
        paidCourse.setSlug("unity-co-ban");
        paidCourse.setStatus(CourseStatus.PUBLISHED);
        paidCourse.setIsFree(false);
        paidCourse.setPrice(new BigDecimal("299000"));
        paidCourse.setInstructor(instructor);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(paidCourse));
        lenient().when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
            CartItem item = inv.getArgument(0);
            item.setId(500L);
            return item;
        });
    }

    @Test
    void addItem_paidPublishedNotOwned_savesAndReturnsItem() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(cartItemRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.empty());

        ItemRes result = cartService.addItem(EMAIL, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
        assertThat(result.courseTitle()).isEqualTo("Unity co ban");
        assertThat(result.instructorName()).isEqualTo("Co Lan");
        assertThat(result.price()).isEqualByComparingTo("299000");
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCourse()).isEqualTo(paidCourse);
    }

    @Test
    void addItem_alreadyInCart_returnsExistingWithoutCreatingDuplicate() {
        CartItem existing = new CartItem();
        existing.setId(501L);
        existing.setUser(user);
        existing.setCourse(paidCourse);
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(cartItemRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(existing));

        ItemRes result = cartService.addItem(EMAIL, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_freeCourse_throws() {
        paidCourse.setIsFree(true);

        assertThatThrownBy(() -> cartService.addItem(EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_notPublished_throws() {
        paidCourse.setStatus(CourseStatus.DRAFT);

        assertThatThrownBy(() -> cartService.addItem(EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_alreadyOwned_throws() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> cartService.addItem(EMAIL, 10L))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void getMyCart_returnsAllItemsForUser() {
        CartItem item = new CartItem();
        item.setId(500L);
        item.setUser(user);
        item.setCourse(paidCourse);
        when(cartItemRepository.findByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(item));

        List<ItemRes> result = cartService.getMyCart(EMAIL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseId()).isEqualTo(10L);
    }

    @Test
    void removeItem_deletesByUserAndCourse() {
        cartService.removeItem(EMAIL, 10L);

        verify(cartItemRepository).deleteByUser_IdAndCourse_Id(1L, 10L);
    }
}
