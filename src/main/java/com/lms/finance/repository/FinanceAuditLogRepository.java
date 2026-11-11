package com.lms.finance.repository;

import com.lms.finance.entity.FinanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinanceAuditLogRepository extends JpaRepository<FinanceAuditLog, Long> {
}
