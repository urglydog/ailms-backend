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

    org.springframework.data.domain.Page<InstructorLedgerEntry> findByInstructor_IdOrderByCreatedAtDescIdDesc(
            Long instructorId, org.springframework.data.domain.Pageable pageable);
}
