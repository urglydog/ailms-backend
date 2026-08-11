package com.lms.dubbing.service;

import com.lms.common.enums.Role;
import com.lms.common.exception.QuotaExceededException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Hạn ngạch yêu cầu lồng tiếng theo ngày (BR-DUB-06): Student 15/ngày, Instructor 30/ngày
 * (Instructor "pre-warm" — tự tạo bản lồng tiếng trước khi học viên vào học). Counter Redis
 * hết hạn lúc 00:00 hôm sau, cùng khuôn với {@code OtpService}.
 */
@Service
@RequiredArgsConstructor
public class DubbingQuotaService {

    private static final String QUOTA_PREFIX = "dub_quota:";

    private final StringRedisTemplate redisTemplate;

    @Value("${lms.quota.dub-student-per-day}")
    private long studentPerDay;

    @Value("${lms.quota.dub-instructor-per-day}")
    private long instructorPerDay;

    /**
     * Tăng bộ đếm và ném {@link QuotaExceededException} nếu vượt hạn ngạch. Gọi TRƯỚC khi tạo
     * {@code AiJob} — lần bấm bị dedupe bởi BR-DUB-04/05 KHÔNG được gọi hàm này (không tính hạn ngạch).
     */
    public void consume(Long userId, Role role) {
        long limit = role == Role.INSTRUCTOR ? instructorPerDay : studentPerDay;
        String key = QUOTA_PREFIX + LocalDate.now() + ":" + userId;

        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, secondsUntilMidnight());
        }
        if (current != null && current > limit) {
            throw new QuotaExceededException("yêu cầu lồng tiếng/ngày", limit);
        }
    }

    private Duration secondsUntilMidnight() {
        return Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay());
    }
}
