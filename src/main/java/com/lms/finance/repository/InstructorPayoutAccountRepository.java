package com.lms.finance.repository;

import com.lms.common.enums.PayoutAccountStatus;
import com.lms.finance.entity.InstructorPayoutAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorPayoutAccountRepository extends JpaRepository<InstructorPayoutAccount, Long> {

    Optional<InstructorPayoutAccount> findFirstByUser_IdAndStatus(Long userId, PayoutAccountStatus status);

    List<InstructorPayoutAccount> findByUser_IdAndStatus(Long userId, PayoutAccountStatus status);

    Optional<InstructorPayoutAccount> findFirstByUser_IdAndStatusAndIsDefaultTrue(Long userId, PayoutAccountStatus status);

    List<InstructorPayoutAccount> findByUser_IdAndStatusInOrderByIdAsc(Long userId, java.util.Collection<PayoutAccountStatus> statuses);

    Optional<InstructorPayoutAccount> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndStatus(Long userId, PayoutAccountStatus status);

    boolean existsByUser_IdAndAccountHashAndStatus(Long userId, String accountHash, PayoutAccountStatus status);

    /** Cùng 1 số tài khoản đang được giảng viên KHÁC dùng (phát hiện gian lận/nhầm lẫn). */
    boolean existsByAccountHashAndStatusAndUser_IdNot(String accountHash, PayoutAccountStatus status, Long userId);
}
