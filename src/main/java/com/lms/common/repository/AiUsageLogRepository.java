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

    /**
     * Tiêu thụ AI theo người dùng trong khoảng thời gian (20/11/2026) — tìm theo email/tên/ID, lọc đang bị khóa AI và theo tính năng, sắp xếp theo chi phí giảm dần,
     * LUÔN phân trang (thay cho việc trả toàn bộ người dùng).
     */
    @Query(value = "SELECT u.id as userId, u.email as email, u.isAiLocked as isAiLocked, SUM(l.totalTokens) as totalTokens, SUM(l.costUsd) as totalCost, COUNT(l) as requests "
            + "FROM AiUsageLog l JOIN User u ON l.userId = u.id WHERE l.createdAt >= :from AND l.createdAt < :to "
            + "AND (:q IS NULL OR LOWER(u.email) LIKE :q OR LOWER(u.fullName) LIKE :q OR u.id = :qId) "
            + "AND (:lockedOnly = false OR u.isAiLocked = true) AND (:feature IS NULL OR l.featureType = :feature) "
            + "GROUP BY u.id, u.email, u.isAiLocked ORDER BY SUM(l.costUsd) DESC",
            countQuery = "SELECT COUNT(DISTINCT u.id) FROM AiUsageLog l JOIN User u ON l.userId = u.id WHERE l.createdAt >= :from AND l.createdAt < :to "
            + "AND (:q IS NULL OR LOWER(u.email) LIKE :q OR LOWER(u.fullName) LIKE :q OR u.id = :qId) "
            + "AND (:lockedOnly = false OR u.isAiLocked = true) AND (:feature IS NULL OR l.featureType = :feature)")
    org.springframework.data.domain.Page<Map<String, Object>> searchUsage(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                                          @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
                                                                          @org.springframework.data.repository.query.Param("q") String q,
                                                                          @org.springframework.data.repository.query.Param("qId") long qId,
                                                                          @org.springframework.data.repository.query.Param("lockedOnly") boolean lockedOnly,
                                                                          @org.springframework.data.repository.query.Param("feature") String feature,
                                                                          org.springframework.data.domain.Pageable pageable);

    /** Các tính năng AI đã từng có log (cho bộ lọc). */
    @Query("SELECT DISTINCT l.featureType FROM AiUsageLog l ORDER BY l.featureType")
    List<String> distinctFeatures();

    /** Tổng token/chi phí theo ngày trong khoảng [from, to) — cho biểu đồ theo khoảng ngày được chọn. */
    @Query("SELECT FUNCTION('DATE', l.createdAt) as day, SUM(l.totalTokens) as totalTokens, SUM(l.costUsd) as totalCost "
            + "FROM AiUsageLog l WHERE l.createdAt >= :from AND l.createdAt < :to AND (:feature IS NULL OR l.featureType = :feature) "
            + "GROUP BY FUNCTION('DATE', l.createdAt) ORDER BY day ASC")
    List<Map<String, Object>> getDailyUsageBetween(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                   @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
                                                   @org.springframework.data.repository.query.Param("feature") String feature);

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

    /** Auto-ban tín hiệu 3 (27/09/2026) — tổng token tiêu thụ của 1 user trong khoảng thời gian
     * gần nhất, chống Denial of Wallet qua Prompt Injection (1 request "nhồi" quá nhiều token
     * vẫn bị bắt, khác tín hiệu 2 chỉ đếm SỐ LƯỢNG request). */
    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiUsageLog l WHERE l.userId = :userId AND l.createdAt >= :since")
    long getTotalTokensForUserSince(Long userId, java.time.LocalDateTime since);
}
