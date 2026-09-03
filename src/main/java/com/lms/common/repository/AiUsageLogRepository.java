package com.lms.common.repository;

import com.lms.common.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
    
    @Query("SELECT l.user.id as userId, l.user.email as email, l.user.isAiLocked as isAiLocked, SUM(l.totalTokens) as totalTokens, SUM(l.costUsd) as totalCost " +
           "FROM AiUsageLog l GROUP BY l.user.id, l.user.email, l.user.isAiLocked ORDER BY totalCost DESC")
    List<Map<String, Object>> getUsageSummaryByUser();
}
