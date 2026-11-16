package com.lms.finance.payos;

import com.lms.common.enums.PayosOrderKind;
import com.lms.common.exception.BusinessRuleViolationException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Khóa Redis ngắn cho thao tác chi PayOS của MỘT khoản (16/11/2026): hai lần bấm cùng lúc thì chỉ một lần được vào, lần kia nhận thông báo rõ ràng
 * thay vì lỗi 500 do vi phạm khóa duy nhất. Đây là lớp thứ ba, sau ràng buộc UNIQUE(reference_id) ở DB và khóa idempotency gửi PayOS — nên Redis
 * chỉ để trải nghiệm tốt hơn, KHÔNG phải chốt an toàn duy nhất. Redis lỗi thì từ chối (thao tác tiền: thà dừng còn hơn đoán), cùng cách với
 * {@code FinanceConfirmationService}.
 */
@Component
@RequiredArgsConstructor
public class PayosTransferLock {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final String PREFIX = "payos:transfer:";
    /** Chỉ xóa khóa nếu vẫn là của mình (phòng khóa đã hết hạn và người khác giữ). */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;

    /** Giữ khóa trong khối try-with-resources; đóng thì nhả. */
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    public Handle acquire(PayosOrderKind kind, Long entityId) {
        String key = PREFIX + kind.name() + ":" + entityId;
        String token = UUID.randomUUID().toString();
        Boolean ok;
        try {
            ok = redis.opsForValue().setIfAbsent(key, token, TTL);
        } catch (RuntimeException e) {
            throw new BusinessRuleViolationException("Không khóa được thao tác chi (hệ thống khóa tạm lỗi); vui lòng thử lại sau ít phút");
        }
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessRuleViolationException("Khoản này đang được một thao tác chi khác xử lý; vui lòng chờ vài giây rồi kiểm tra lại trạng thái");
        }
        return () -> {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException ignored) {
                // Khóa tự hết hạn sau TTL nên không cần làm gì thêm.
            }
        };
    }
}
