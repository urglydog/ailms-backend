package com.lms.payment.repository;

import com.lms.payment.entity.PaymentRefund;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByPayment_Id(Long paymentId);

    boolean existsByPayment_Id(Long paymentId);

    List<PaymentRefund> findByPayment_IdIn(Collection<Long> paymentIds);

    /** Số lần học viên đã được hoàn tiền kể từ mốc thời gian (cảnh báo lạm dụng). */
    long countByPayment_User_IdAndApprovedAtAfter(Long userId, java.time.LocalDateTime since);
}
