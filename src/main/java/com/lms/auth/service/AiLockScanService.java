package com.lms.auth.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.repository.AiUsageLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-ban bằng AI (25/09/2026) — thiết kế human-in-the-loop: job định kỳ (gọi bởi Celery beat
 * của ai-worker, xem {@code app/tasks/maintenance.py::scan_ai_lock_proposals}) quét tín hiệu bất
 * thường và chỉ tạo ĐỀ XUẤT khoá ({@code User.aiLockProposedAt/Reason}) — KHÔNG tự khoá ngay.
 * Admin xem lý do cụ thể ở `GET /api/v1/admin/ai-lock-proposals` rồi mới quyết định.
 *
 * <p>2 tín hiệu (rút gọn từ thiết kế gốc 3 tín hiệu — tín hiệu thứ 3 "Gemini phân loại nội dung
 * prompt" không có dữ liệu để đọc vì {@code AiUsageLog} không lưu nội dung prompt, chỉ lưu số
 * token):
 * <ol>
 *   <li>Quota gần cạn LIÊN TỤC N ngày gần nhất (mặc định 3) — số request/ngày đạt/vượt
 *       {@code daily-request-threshold}.</li>
 *   <li>Tần suất bất thường — có phút nào trong 24h gần nhất vượt {@code per-minute-threshold}
 *       request (dấu hiệu script/bot).</li>
 * </ol>
 * Đạt 1 trong 2 tín hiệu → tạo đề xuất (chỉ khi chưa bị khoá và chưa có đề xuất đang chờ xử lý,
 * tránh ghi đè đề xuất cũ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLockScanService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final UserRepository userRepository;

    @Value("${lms.ai-lock.daily-request-threshold}")
    private long dailyRequestThreshold;

    @Value("${lms.ai-lock.consecutive-days}")
    private int consecutiveDays;

    @Value("${lms.ai-lock.per-minute-threshold}")
    private long perMinuteThreshold;

    @Transactional
    public int scan() {
        LocalDateTime lookback = LocalDate.now().minusDays(consecutiveDays).atStartOfDay();
        List<Long> activeUserIds = aiUsageLogRepository.findDistinctActiveUserIdsSince(lookback);

        int proposalsCreated = 0;
        for (Long userId : activeUserIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || Boolean.TRUE.equals(user.getIsAiLocked()) || user.getAiLockProposedAt() != null) {
                continue; // Đã khoá hoặc đã có đề xuất đang chờ Admin xử lý — không ghi đè.
            }

            String reason = detectSignal(userId);
            if (reason != null) {
                user.setAiLockProposedAt(LocalDateTime.now());
                user.setAiLockProposedReason(reason);
                userRepository.save(user);
                proposalsCreated++;
                log.info("Tao de xuat khoa AI cho user {}: {}", userId, reason);
            }
        }
        return proposalsCreated;
    }

    private String detectSignal(Long userId) {
        // Tín hiệu 1 — quota gần cạn liên tục N ngày gần nhất.
        List<Map<String, Object>> dailyCounts = aiUsageLogRepository.getDailyRequestCountForUser(
                userId, LocalDate.now().minusDays(consecutiveDays).atStartOfDay());
        long consecutiveHighDays = 0;
        for (Map<String, Object> row : dailyCounts) {
            long count = ((Number) row.get("requestCount")).longValue();
            if (count >= dailyRequestThreshold) {
                consecutiveHighDays++;
            } else {
                break; // dailyCounts đã sắp xếp DESC theo ngày — gãy chuỗi liên tục thì dừng.
            }
        }
        if (consecutiveHighDays >= consecutiveDays) {
            return String.format("Dùng AI ở mức cao (>= %d request/ngày) liên tục %d ngày gần nhất.",
                    dailyRequestThreshold, consecutiveHighDays);
        }

        // Tín hiệu 2 — tần suất bất thường trong 24h gần nhất.
        List<Map<String, Object>> perMinute = aiUsageLogRepository.getPerMinuteRequestCountForUser(
                userId, LocalDateTime.now().minusHours(24));
        if (!perMinute.isEmpty()) {
            long maxPerMinute = ((Number) perMinute.get(0).get("requestCount")).longValue();
            if (maxPerMinute > perMinuteThreshold) {
                return String.format("Tần suất bất thường: %d request trong 1 phút (ngưỡng %d) — dấu hiệu script/bot.",
                        maxPerMinute, perMinuteThreshold);
            }
        }

        return null;
    }
}
