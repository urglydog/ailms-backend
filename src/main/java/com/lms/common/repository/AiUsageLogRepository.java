package com.lms.common.repository;

import com.lms.common.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
    
    @Query("SELECT u.id as userId, u.email as email, u.isAiLocked as isAiLocked, SUM(l.totalTokens) as totalTokens, SUM(l.costUsd) as totalCost " +
           "FROM AiUsageLog l JOIN User u ON l.userId = u.id GROUP BY u.id, u.email, u.isAiLocked ORDER BY totalCost DESC")
    List<Map<String, Object>> getUsageSummaryByUser();
}
