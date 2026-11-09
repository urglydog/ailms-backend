package com.lms.dubbing.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Khóa phân tán Redis chống trùng job lồng tiếng (BR-DUB-05) — lớp thứ hai bên cạnh
 * UNIQUE {@code (lesson_id, target_language, active_flag)} ở DB. Cần CẢ HAI lớp: một
 * mình UNIQUE DB không chặn được race giữa nhiều instance backend (transaction đọc rồi
 * mới ghi, chưa commit kịp).
 */
@Service
@RequiredArgsConstructor
public class DubbingLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${lms.redis-keys.dub-lock-prefix}")
    private String lockPrefix;

    @Value("${lms.dub-lock-ttl-seconds}")
    private long ttlSeconds;

    /** SETNX — true nếu giành được khóa, false nếu đã có job khác đang giữ. */
    public boolean tryLock(Long lessonId, String targetLanguage) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(lessonId, targetLanguage), "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    /** Phải gọi ở CẢ nhánh thành công lẫn nhánh lỗi, không được để khóa treo đến hết TTL. */
    public void release(Long lessonId, String targetLanguage) {
        redisTemplate.delete(lockKey(lessonId, targetLanguage));
    }

    private String lockKey(Long lessonId, String targetLanguage) {
        return lockPrefix + lessonId + ":" + targetLanguage;
    }
}
