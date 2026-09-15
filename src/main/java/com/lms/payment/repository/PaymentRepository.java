package com.lms.payment.repository;

import com.lms.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link Payment}.
 *
 * <p>Giai doan 0 chi khai bao. Cac phuong thuc truy van duoc them dan o giai doan
 * dung den, kem {@code @EntityGraph} khi can nap quan he de tranh N+1.
 */
import com.lms.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTxnRef(String txnRef);
    List<Payment> findByUser_EmailOrderByCreatedAtDesc(String email);
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);

    /** Giỏ hàng (06/09/2026) — các Payment tạo cùng 1 lần gộp thanh toán chia sẻ chung giá
     * trị này, xem {@code Payment.orderGroupRef}. */
    List<Payment> findByOrderGroupRef(String orderGroupRef);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "course"})
    List<Payment> findAllByOrderByCreatedAtDesc();

    /** Hạn mức coupon (15/09/2026, mở rộng) — chỉ đếm giao dịch đã THÀNH CÔNG, tránh giỏ hàng
     * bỏ dở/thất bại chiếm suất dùng của người khác. */
    long countByCoupon_IdAndStatus(Long couponId, PaymentStatus status);

    long countByCoupon_IdAndUser_IdAndStatus(Long couponId, Long userId, PaymentStatus status);

    /** Dashboard Giảng viên (15/09/2026, sửa lỗi) — doanh thu THỰC NHẬN (đã trừ phí nền tảng
     * BR-PAY-05) từ đầu tháng tới nay, thay số "15400000" gắn cứng cũ (đang chờ module Payment
     * — module đã có thật). */
    @Query("SELECT COALESCE(SUM(p.instructorEarning), 0) FROM Payment p "
            + "WHERE p.course.instructor.email = :email AND p.status = com.lms.common.enums.PaymentStatus.PAID "
            + "AND p.paidAt >= :monthStart")
    BigDecimal sumInstructorEarningSince(@Param("email") String email, @Param("monthStart") LocalDateTime monthStart);
}
