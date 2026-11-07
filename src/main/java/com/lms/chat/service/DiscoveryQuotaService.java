package com.lms.chat.service;

import com.lms.common.exception.QuotaExceededException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Hạn ngạch Discovery Chat (BR-DISCOVERY-01): Khách 15 tin/IP/giờ (cửa sổ tính từ tin đầu tiên),
 * Học viên 30 tin/ngày (reset 0h theo giờ máy chủ — Asia/Ho_Chi_Minh). Cùng khuôn Redis INCR +
 * hết hạn với {@link TutorQuotaService}. Bộ đếm độc lập hoàn toàn với khóa AI/spam của tài khoản.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryQuotaService {

    private static final String GUEST_PREFIX = "discovery_quota:ip:";
    private static final String STUDENT_PREFIX = "discovery_quota:user:";

    private final StringRedisTemplate redisTemplate;

    @Value("${lms.quota.discovery-guest-per-hour}")
    private long guestPerHour;

    @Value("${lms.quota.discovery-student-per-day}")
    private long studentPerDay;

    /** Khách (không đăng nhập) — đếm theo IP. */
    public void consumeGuest(String ip) {
        String key = GUEST_PREFIX + (ip == null || ip.isBlank() ? "unknown" : ip);
        if (increment(key, Duration.ofHours(1)) > guestPerHour) {
            throw new QuotaExceededException("Bạn đã dùng hết " + guestPerHour
                    + " lượt hỏi trong 1 giờ. Vui lòng thử lại sau hoặc đăng nhập để hỏi thêm.");
        }
    }

    /** Học viên — đếm theo userId, hết hạn lúc nửa đêm. */
    public void consumeStudent(Long userId) {
        String key = STUDENT_PREFIX + LocalDate.now() + ":" + userId;
        Duration untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
        if (increment(key, untilMidnight) > studentPerDay) {
            throw new QuotaExceededException("Bạn đã dùng hết " + studentPerDay
                    + " lượt hỏi hôm nay. Lượt mới sẽ có lúc 0h.");
        }
    }

    private long increment(String key, Duration ttlOnFirst) {
        Long current = redisTemplate.opsForValue().increment(key);
        if (current == null) {
            return 0L;
        }
        if (current == 1L) {
            redisTemplate.expire(key, ttlOnFirst);
        }
        return current;
    }
}
