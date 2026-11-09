package com.lms.common.ratelimit;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Bộ đếm cửa sổ cố định 1 phút trong Redis: {@code INCR key} + {@code EXPIRE} ở lần đầu.
 * Dùng chung được giữa nhiều instance backend (khác bộ đếm trong RAM).
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    static final long WINDOW_SEC = 60;

    private final StringRedisTemplate redis;

    /** @return số giây phải chờ nếu vượt giới hạn; 0 nếu được phép. */
    public long hit(String bucket, String clientKey, int limit) {
        long nowSec = System.currentTimeMillis() / 1000;
        long window = nowSec / WINDOW_SEC;
        String key = "rl:" + bucket + ":" + clientKey + ":" + window;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(WINDOW_SEC + 5));
        }
        if (count != null && count > limit) {
            return Math.max(1, (window + 1) * WINDOW_SEC - nowSec);
        }
        return 0;
    }
}
