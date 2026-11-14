package com.lms.finance.repository;

import com.lms.common.enums.DebtPaymentStatus;
import com.lms.finance.entity.InstructorDebtPayment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorDebtPaymentRepository extends JpaRepository<InstructorDebtPayment, Long> {

    Optional<InstructorDebtPayment> findByOrderCode(Long orderCode);

    List<InstructorDebtPayment> findByInstructor_IdOrderByIdDesc(Long instructorId);

    List<InstructorDebtPayment> findByInstructor_IdAndStatus(Long instructorId, DebtPaymentStatus status);

    List<InstructorDebtPayment> findByStatusIn(Collection<DebtPaymentStatus> statuses);

    boolean existsByOrderCode(Long orderCode);
}
