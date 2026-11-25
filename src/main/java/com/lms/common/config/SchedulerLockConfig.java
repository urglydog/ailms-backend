package com.lms.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Hiệu năng đợt 6 (A2): khóa phân tán cho các job {@code @Scheduled} bằng Redis (đã có sẵn trong hạ tầng).
 *
 * <p>Khi chạy từ 2 backend trở lên, mỗi job có {@code @SchedulerLock} chỉ chạy trên MỘT instance mỗi lượt,
 * tránh gửi trùng email, xuất trùng hóa đơn, đối soát thanh toán hai lần. Khóa tự hết hạn sau
 * {@code lockAtMostFor} nếu instance giữ khóa bị sập giữa chừng.
 *
 * <p>Cố ý KHÔNG khóa: {@code TutorSecurityService.refreshPatterns} (nạp cache trong RAM, instance nào cũng
 * phải tự chạy) và 2 job {@code consumeQueue} (đã lấy việc bằng RPOP nguyên tử của Redis nên an toàn).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "lms");
    }
}
