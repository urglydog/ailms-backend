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

    /** Auto-ban tín hiệu 1 (25/09/2026) — số request/ngày của 1 user trong N ngày gần nhất, để
     * `AiLockScanService` kiểm tra "đạt/vượt ngưỡng LIÊN TỤC bao nhiêu ngày". */
    @Query("SELECT FUNCTION('DATE', l.createdAt) as day, COUNT(l) as requestCount " +
           "FROM AiUsageLog l WHERE l.userId = :userId AND l.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY day DESC")
    List<Map<String, Object>> getDailyRequestCountForUser(Long userId, java.time.LocalDateTime since);

    /** Auto-ban tín hiệu 2 (25/09/2026) — số request/phút của 1 user trong 24h gần nhất, phát
     * hiện tần suất bất thường (dấu hiệu script/bot). */
    @Query("SELECT FUNCTION('DATE_FORMAT', l.createdAt, '%Y-%m-%d %H:%i') as minute, COUNT(l) as requestCount " +
           "FROM AiUsageLog l WHERE l.userId = :userId AND l.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE_FORMAT', l.createdAt, '%Y-%m-%d %H:%i') ORDER BY requestCount DESC")
    List<Map<String, Object>> getPerMinuteRequestCountForUser(Long userId, java.time.LocalDateTime since);

    /** Danh sách user duy nhất có hoạt động AI trong N ngày gần nhất — chỉ những user này mới
     * cần quét tín hiệu, tránh duyệt toàn bộ bảng `users`. */
    @Query("SELECT DISTINCT l.userId FROM AiUsageLog l WHERE l.createdAt >= :since")
    List<Long> findDistinctActiveUserIdsSince(java.time.LocalDateTime since);
}
