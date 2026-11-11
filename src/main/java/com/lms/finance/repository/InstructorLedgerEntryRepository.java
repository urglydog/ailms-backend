package com.lms.finance.repository;

import com.lms.common.enums.LedgerEntryType;
import com.lms.finance.entity.InstructorLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstructorLedgerEntryRepository extends JpaRepository<InstructorLedgerEntry, Long> {

    boolean existsByPayment_IdAndEntryType(Long paymentId, LedgerEntryType entryType);
}
