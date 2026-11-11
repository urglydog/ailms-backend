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

    /** Mỗi giao dịch chỉ có 1 khiếu nại (11/11/2026) — chặn nộp lại sau khi đã xử lý (tránh hoàn 2 lần). */
    boolean existsByPayment_Id(Long paymentId);

    /** Khiếu nại thuộc các khóa của 1 giảng viên (inbox giảng viên). */
    @EntityGraph(attributePaths = {"payment", "payment.course", "filedBy"})
    List<PaymentDispute> findByPayment_Course_Instructor_EmailOrderByCreatedAtDesc(String email);

    /** Job quá hạn: khiếu nại còn mở, giảng viên chưa phản hồi và đã quá hạn. */
    @EntityGraph(attributePaths = {"payment", "payment.course"})
    List<PaymentDispute> findByInstructorDecisionIsNullAndInstructorDeadlineAtBeforeAndStatusIn(
            java.time.LocalDateTime deadline, java.util.Collection<DisputeStatus> statuses);
}
