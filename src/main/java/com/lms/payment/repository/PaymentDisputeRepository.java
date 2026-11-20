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

    /** Danh sách khiếu nại cho admin (20/11/2026): lọc trạng thái + tìm theo mã đơn/email/tên người gửi, luôn phân trang. */
    @EntityGraph(attributePaths = {"payment", "payment.course", "filedBy", "resolvedBy"})
    @org.springframework.data.jpa.repository.Query("SELECT d FROM PaymentDispute d JOIN d.payment p JOIN d.filedBy f "
            + "WHERE (:status IS NULL OR d.status = :status) AND (:q IS NULL OR LOWER(p.txnRef) LIKE :q OR LOWER(f.email) LIKE :q "
            + "OR LOWER(f.fullName) LIKE :q OR d.id = :qId) AND (:needsAction = false OR (d.status IN (com.lms.common.enums.DisputeStatus.OPEN, com.lms.common.enums.DisputeStatus.REOPENED) OR EXISTS (SELECT 1 FROM PaymentRefund r WHERE r.payment = d.payment AND (r.transferStatus = com.lms.common.enums.RefundTransferStatus.PENDING_TRANSFER OR r.appealStatus = com.lms.common.enums.AppealStatus.OPEN)))) ORDER BY d.createdAt DESC")
    List<PaymentDispute> searchForAdmin(@org.springframework.data.repository.query.Param("status") DisputeStatus status,
                                        @org.springframework.data.repository.query.Param("q") String q,
                                        @org.springframework.data.repository.query.Param("qId") long qId,
                                        @org.springframework.data.repository.query.Param("needsAction") boolean needsAction,
                                        org.springframework.data.domain.Pageable pageable);

    /** Số khiếu nại đang cần admin làm gì đó: chưa xử lý/mở lại, đã duyệt hoàn chờ chuyển tiền, hoặc có kháng nghị đang chờ (huy hiệu menu). */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(d) FROM PaymentDispute d WHERE (d.status IN (com.lms.common.enums.DisputeStatus.OPEN, com.lms.common.enums.DisputeStatus.REOPENED) OR EXISTS (SELECT 1 FROM PaymentRefund r WHERE r.payment = d.payment AND (r.transferStatus = com.lms.common.enums.RefundTransferStatus.PENDING_TRANSFER OR r.appealStatus = com.lms.common.enums.AppealStatus.OPEN)))")
    long countNeedingAdminAction();

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
