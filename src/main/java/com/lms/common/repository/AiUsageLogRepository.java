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

    /** Task 11A — tổng token/chi phí AI theo ngày, phục vụ biểu đồ Admin Dashboard.
     * Chỉ lấy 30 ngày gần nhất để đồ thị gọn, không cần tham số. */
    @Query("SELECT FUNCTION('DATE', l.createdAt) as day, SUM(l.totalTokens) as totalTokens, SUM(l.costUsd) as totalCost " +
           "FROM AiUsageLog l WHERE l.createdAt >= :since GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY day ASC")
    List<Map<String, Object>> getDailyUsage(java.time.LocalDateTime since);
}
