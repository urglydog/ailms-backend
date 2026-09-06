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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
}
