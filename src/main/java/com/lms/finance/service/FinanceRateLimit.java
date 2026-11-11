package com.lms.finance.service;

import com.lms.common.exception.QuotaExceededException;
import com.lms.common.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất cho thao tác tiền (11/11/2026, Giai đoạn C) theo TỪNG NGƯỜI DÙNG. Dùng lại {@link RateLimiter}
 * (Redis, cửa sổ 1 phút) nhưng KHÁC bộ lọc toàn cục ở chỗ Redis lỗi thì ném lỗi (từ chối) thay vì cho qua.
 */
@Component
@RequiredArgsConstructor
public class FinanceRateLimit {

    private final RateLimiter rateLimiter;

    public void check(String bucket, Long userId, int perMinute) {
        long waitSeconds = rateLimiter.hit("fin-" + bucket, String.valueOf(userId), perMinute);
        if (waitSeconds > 0) {
            throw new QuotaExceededException("Bạn thao tác quá nhanh, vui lòng thử lại sau " + waitSeconds + " giây.");
        }
    }
}
