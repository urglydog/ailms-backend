package com.lms.payment.repository;

import com.lms.common.enums.DisputeStatus;
import com.lms.payment.entity.PaymentDispute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentDisputeRepository extends JpaRepository<PaymentDispute, Long> {

    @EntityGraph(attributePaths = {"payment", "payment.course", "filedBy"})
    List<PaymentDispute> findByFiledBy_EmailOrderByCreatedAtDesc(String email);

    @EntityGraph(attributePaths = {"payment", "payment.course", "filedBy", "resolvedBy"})
    List<PaymentDispute> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"payment", "payment.course", "filedBy", "resolvedBy"})
    List<PaymentDispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);

    Optional<PaymentDispute> findByPayment_IdAndStatus(Long paymentId, DisputeStatus status);
}
