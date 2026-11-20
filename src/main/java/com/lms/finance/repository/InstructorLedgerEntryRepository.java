package com.lms.finance.repository;

import com.lms.common.enums.LedgerEntryType;
import com.lms.finance.entity.InstructorLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorLedgerEntryRepository extends JpaRepository<InstructorLedgerEntry, Long> {

    boolean existsByPayment_IdAndEntryType(Long paymentId, LedgerEntryType entryType);

    java.util.Optional<InstructorLedgerEntry> findByPayment_IdAndEntryType(Long paymentId, LedgerEntryType entryType);

    boolean existsByPayoutIdAndEntryType(Long payoutId, LedgerEntryType entryType);

    /** Số dư khả dụng thô = tổng các dòng đã "chín" (kể cả dòng âm: hoàn tiền, giữ chỗ rút). */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(e.amount), 0) FROM InstructorLedgerEntry e "
            + "WHERE e.instructor.id = :instructorId AND e.availableAt <= :now")
    java.math.BigDecimal sumAvailable(@org.springframework.data.repository.query.Param("instructorId") Long instructorId,
                                      @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /** Các giảng viên có số dư khả dụng ÂM (đang nợ) và số nợ của họ — dùng cho việc nền theo dõi công nợ. */
    @org.springframework.data.jpa.repository.Query("SELECT e.instructor.id, SUM(e.amount) FROM InstructorLedgerEntry e "
            + "WHERE e.availableAt <= :now GROUP BY e.instructor.id HAVING SUM(e.amount) < 0")
    java.util.List<Object[]> findDebtors(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    boolean existsByDebtPaymentId(Long debtPaymentId);

    java.util.List<InstructorLedgerEntry> findTop5ByInstructor_IdAndEntryTypeOrderByCreatedAtDescIdDesc(Long instructorId,
            com.lms.common.enums.LedgerEntryType entryType);

    /** Đang giữ = các dòng chưa tới hạn (thu nhập mới trong thời gian hoàn tiền). */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(e.amount), 0) FROM InstructorLedgerEntry e "
            + "WHERE e.instructor.id = :instructorId AND e.availableAt > :now")
    java.math.BigDecimal sumPending(@org.springframework.data.repository.query.Param("instructorId") Long instructorId,
                                    @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /** Đóng băng = thu nhập đã chín nhưng đơn đang có khiếu nại mở (chưa biết có bị hoàn hay không). */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(e.amount), 0) FROM InstructorLedgerEntry e "
            + "WHERE e.instructor.id = :instructorId AND e.availableAt <= :now "
            + "AND e.entryType = com.lms.common.enums.LedgerEntryType.EARNING "
            + "AND e.payment.id IN (SELECT d.payment.id FROM PaymentDispute d WHERE d.status IN "
            + "(com.lms.common.enums.DisputeStatus.OPEN, com.lms.common.enums.DisputeStatus.REOPENED))")
    java.math.BigDecimal sumFrozen(@org.springframework.data.repository.query.Param("instructorId") Long instructorId,
                                   @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

    /** Sao kê có lọc nhóm loại khoản + khoảng ngày (20/11/2026), mới nhất trước, luôn phân trang. */
    @org.springframework.data.jpa.repository.Query("SELECT e FROM InstructorLedgerEntry e WHERE e.instructor.id = :id AND (:allTypes = true OR e.entryType IN :types) "
            + "AND (:from IS NULL OR e.createdAt >= :from) AND (:to IS NULL OR e.createdAt < :to) ORDER BY e.createdAt DESC, e.id DESC")
    org.springframework.data.domain.Page<InstructorLedgerEntry> search(@org.springframework.data.repository.query.Param("id") Long instructorId,
            @org.springframework.data.repository.query.Param("allTypes") boolean allTypes,
            @org.springframework.data.repository.query.Param("types") java.util.Collection<com.lms.common.enums.LedgerEntryType> types,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<InstructorLedgerEntry> findByInstructor_IdOrderByCreatedAtDescIdDesc(
            Long instructorId, org.springframework.data.domain.Pageable pageable);
}
