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
 * Hạn ngạch hỏi Gia sư AI theo ngày (BR-TUTOR-04): 30 tin/học viên/ngày. Cùng khuôn
 * Redis INCR + hết hạn nửa đêm với {@link com.lms.dubbing.service.DubbingQuotaService}.
 */
@Service
@RequiredArgsConstructor
public class TutorQuotaService {

    private static final String QUOTA_PREFIX = "tutor_quota:";

    private final StringRedisTemplate redisTemplate;

    @Value("${lms.quota.tutor-msg-per-day}")
    private long msgPerDay;

    /** Tăng bộ đếm và ném {@link QuotaExceededException} nếu vượt hạn ngạch. */
    public void consume(Long userId) {
        String key = QUOTA_PREFIX + LocalDate.now() + ":" + userId;

        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, secondsUntilMidnight());
        }
        if (current != null && current > msgPerDay) {
            throw new QuotaExceededException("tin nhắn hỏi Gia sư AI/ngày", msgPerDay);
        }
    }

    private Duration secondsUntilMidnight() {
        return Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
    }
}
