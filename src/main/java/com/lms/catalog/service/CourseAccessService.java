package com.lms.catalog.service;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseInviteRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Đăng ký (Quyền riêng tư)" kiểu Udemy (19/09/2026) — dùng CHUNG bởi mọi điểm ghi danh
 * (miễn phí, giỏ hàng, mua trực tiếp) để không lặp lại logic kiểm PRIVATE_INVITE/PRIVATE_PASSWORD
 * ở 3 service khác nhau (EnrollmentService, CartService, PaymentService).
 */
@Service
@RequiredArgsConstructor
public class CourseAccessService {

    private final CourseInviteRepository courseInviteRepository;
    private final PasswordEncoder passwordEncoder;

    /** Giảng viên sở hữu hoặc email nằm trong danh sách mời — dùng cho cả trang chi tiết
     * (PRIVATE_INVITE ẩn hẳn với người ngoài) lẫn lúc ghi danh. */
    @Transactional(readOnly = true)
    public boolean isInvited(Course course, String email) {
        if (email == null) {
            return false;
        }
        if (course.getInstructor() != null && course.getInstructor().getEmail().equals(email)) {
            return true;
        }
        return courseInviteRepository.existsByCourse_IdAndEmail(course.getId(), email);
    }

    /**
     * Ném lỗi nếu KHÔNG đủ điều kiện ghi danh theo {@code visibility} — gọi ở đúng thời điểm
     * học viên "chốt" muốn sở hữu khóa (ghi danh miễn phí / thêm giỏ hàng / mua trực tiếp),
     * KHÔNG phải lúc chỉ xem trang chi tiết.
     */
    @Transactional(readOnly = true)
    public void verifyCanEnroll(Course course, String email, String password) {
        switch (course.getVisibility()) {
            case PRIVATE_INVITE -> {
                if (!isInvited(course, email)) {
                    throw new AccessDeniedDomainException(
                            "Khóa học này chỉ dành cho người được Giảng viên mời.");
                }
            }
            case PRIVATE_PASSWORD -> {
                if (course.getEnrollPasswordHash() == null
                        || password == null || password.isBlank()
                        || !passwordEncoder.matches(password, course.getEnrollPasswordHash())) {
                    throw new AccessDeniedDomainException("Mật khẩu đăng ký không đúng.");
                }
            }
            case PUBLIC -> {
                // Không có điều kiện gì thêm.
            }
        }
    }

    /**
     * Dùng riêng cho giỏ hàng/thanh toán gộp nhiều khóa (1 lần "Proceed to Checkout" dùng
     * chung 1 {@code couponCode}, KHÔNG có chỗ cho mật khẩu riêng từng khóa) — vì vậy
     * {@code PRIVATE_PASSWORD} luôn bị từ chối ở đây dù mật khẩu có đúng hay không, học viên
     * phải dùng "Mua ngay" (mua trực tiếp, xem {@link #verifyCanEnroll}) cho khóa đó. Khác
     * {@code PRIVATE_INVITE} — không cần bí mật nào để kiểm nên vẫn hoạt động bình thường ở
     * giỏ hàng.
     */
    @Transactional(readOnly = true)
    public void verifyCanAddToCart(Course course, String email) {
        switch (course.getVisibility()) {
            case PRIVATE_INVITE -> {
                if (!isInvited(course, email)) {
                    throw new AccessDeniedDomainException(
                            "Khóa học này chỉ dành cho người được Giảng viên mời.");
                }
            }
            case PRIVATE_PASSWORD -> throw new AccessDeniedDomainException(
                    "Khóa học riêng tư có mật khẩu chỉ hỗ trợ mua trực tiếp, không thể thêm vào giỏ hàng.");
            case PUBLIC -> {
                // Không có điều kiện gì thêm.
            }
        }
    }
}
