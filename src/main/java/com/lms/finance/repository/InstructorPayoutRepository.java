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

    @EntityGraph(attributePaths = {"instructor"})
    List<InstructorPayout> findByStatusOrderByCreatedAtDesc(PayoutStatus status);

    boolean existsByCode(String code);

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
