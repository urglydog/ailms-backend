package com.lms.finance.repository;

import com.lms.common.enums.PayoutStatus;
import com.lms.finance.entity.InstructorPayout;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorPayoutRepository extends JpaRepository<InstructorPayout, Long> {

    List<InstructorPayout> findByInstructor_IdOrderByCreatedAtDesc(Long instructorId);

    @EntityGraph(attributePaths = {"instructor"})
    List<InstructorPayout> findAllByOrderByCreatedAtDesc();

    /** Danh sách yêu cầu rút cho admin (20/11/2026): lọc trạng thái + tìm theo mã yêu cầu/email/tên giảng viên, luôn phân trang. */
    @org.springframework.data.jpa.repository.Query("SELECT o FROM InstructorPayout o JOIN o.instructor u WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:q IS NULL OR LOWER(o.code) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(u.fullName) LIKE :q OR o.id = :qId) "
            + "ORDER BY o.createdAt DESC")
    List<InstructorPayout> searchForAdmin(@org.springframework.data.repository.query.Param("status") PayoutStatus status,
                                          @org.springframework.data.repository.query.Param("q") String q,
                                          @org.springframework.data.repository.query.Param("qId") long qId,
                                          org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"instructor"})
    List<InstructorPayout> findByStatusOrderByCreatedAtDesc(PayoutStatus status);

    boolean existsByCode(String code);

    /** Có yêu cầu rút nào còn mở đang dùng tài khoản này không (chặn gỡ tài khoản giữa chừng). */
    boolean existsByAccount_IdAndStatusIn(Long accountId, Collection<PayoutStatus> statuses);

    boolean existsByInstructor_IdAndStatusIn(Long instructorId, Collection<PayoutStatus> statuses);

    Optional<InstructorPayout> findByInstructor_IdAndIdempotencyKey(Long instructorId, String idempotencyKey);

    List<InstructorPayout> findByStatusAndCreatedAtBefore(PayoutStatus status, LocalDateTime before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM InstructorPayout p WHERE p.id = :id")
    Optional<InstructorPayout> findByIdForUpdate(@Param("id") Long id);

    /** Tổng đã chi thật cho giảng viên (PAID). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM InstructorPayout p WHERE p.instructor.id = :instructorId AND p.status = com.lms.common.enums.PayoutStatus.PAID")
    BigDecimal sumPaid(@Param("instructorId") Long instructorId);

    long countByStatus(PayoutStatus status);
}
