package com.lms.auth.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Danh sách "đen" user bị Admin khóa tài khoản (23/10/2026) — thu hồi access token hiện hành
 * NGAY LẬP TỨC thay vì chờ hết hạn tự nhiên.
 *
 * <p><b>Bug thật đã sửa:</b> trước đây {@link CustomUserDetails#isAccountNonLocked()} chỉ được
 * Spring Security đọc LÚC ĐĂNG NHẬP ({@code AuthenticationManager.authenticate()}); với JWT
 * stateless, {@link JwtAuthenticationFilter} dựng {@code Authentication} thẳng từ claim trong
 * token, KHÔNG query lại DB mỗi request — nên Admin khóa tài khoản xong, user vẫn dùng access
 * token cũ (sống tới {@code JWT_ACCESS_TTL}, mặc định 15 phút) bình thường, không hề bị chặn.
 *
 * <p>TTL đặt bằng {@code JWT_REFRESH_TTL} (mặc định 7 ngày) — đủ dài để phủ mọi access token cũ
 * còn sống (access token luôn ngắn hơn refresh token); {@code unlock} xoá khoá sớm hơn khi Admin
 * mở khóa lại, TTL chỉ là lưới an toàn tránh key tồn tại vĩnh viễn nếu quên.
 */
@Component
@RequiredArgsConstructor
public class AccountLockRegistry {

    private static final String KEY_PREFIX = "account:locked:";

    private final StringRedisTemplate redisTemplate;

    public void lock(Long userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, "1", Duration.ofDays(7));
    }

    public void unlock(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    public boolean isLocked(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + userId));
    }
}
