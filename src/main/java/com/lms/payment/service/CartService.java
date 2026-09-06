package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.dto.CartDto;
import com.lms.payment.entity.CartItem;
import com.lms.payment.repository.CartItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc của
 * đồ án. Học viên thêm khóa học TRẢ PHÍ vào giỏ trước khi thanh toán (thay vì chỉ mua
 * ngay từng khóa qua UC13/UC14 — 2 luồng tồn tại song song, không loại trừ nhau).
 *
 * <p><b>BR-CART-01</b> (mở rộng, cùng tinh thần BR-PAY-01/BR-ENROLL-01): chỉ thêm được
 * khóa học {@code PUBLISHED}, TRẢ PHÍ (không phải {@code isFree}), và học viên CHƯA sở
 * hữu — khóa miễn phí ghi danh thẳng qua {@code EnrollmentService.enrollFreeCourse}.
 *
 * <p><b>BR-CART-02</b> (mở rộng): thêm 1 khóa đã có sẵn trong giỏ là thao tác IDEMPOTENT
 * (trả về đúng dòng đã có, không báo lỗi trùng) — giống hành vi giỏ hàng thực tế (Udemy),
 * khác các ràng buộc UNIQUE khác trong hệ thống vốn luôn ném lỗi khi trùng.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public List<CartDto.ItemRes> getMyCart(String email) {
        User user = requireUser(email);
        return cartItemRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(CartService::toRes)
                .toList();
    }

    @Transactional
    public CartDto.ItemRes addItem(String email, Long courseId) {
        User user = requireUser(email);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-CART-01: Chỉ có thể thêm khóa học đã xuất bản vào giỏ hàng.");
        }
        if (Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessRuleViolationException("BR-CART-01: Khóa học miễn phí không cần thêm vào giỏ hàng, hãy ghi danh trực tiếp.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }

        // BR-CART-02: da co san trong gio thi tra ve nguyen dong do, khong tao trung/bao loi.
        CartItem existing = cartItemRepository.findByUser_IdAndCourse_Id(user.getId(), courseId).orElse(null);
        if (existing != null) {
            return toRes(existing);
        }

        CartItem item = new CartItem();
        item.setUser(user);
        item.setCourse(course);
        return toRes(cartItemRepository.save(item));
    }

    @Transactional
    public void removeItem(String email, Long courseId) {
        User user = requireUser(email);
        cartItemRepository.deleteByUser_IdAndCourse_Id(user.getId(), courseId);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private static CartDto.ItemRes toRes(CartItem item) {
        Course c = item.getCourse();
        return new CartDto.ItemRes(
                c.getId(), c.getTitle(), c.getSlug(), c.getThumbnailUrl(),
                c.getInstructor().getFullName(), c.getPrice(), item.getCreatedAt());
    }
}
