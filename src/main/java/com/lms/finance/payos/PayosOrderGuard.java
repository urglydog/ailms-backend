package com.lms.finance.payos;

import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.finance.repository.PayosPayoutOrderRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Chốt chặn ở TẦNG SERVICE (16/11/2026): không cho ghi nhận/hủy/chuyển tay một khoản đang có lệnh chi PayOS chưa chốt. Trước đây kiểm tra này chỉ
 * nằm ở controller nên mọi đường không đi qua controller đó đều không bị chặn; đặt ở service thì gọi API trực tiếp (curl) cũng đi qua cùng một cửa.
 * Chỉ phụ thuộc repository nên không tạo vòng phụ thuộc giữa các service.
 */
@Component
@RequiredArgsConstructor
public class PayosOrderGuard {

    /** Lệnh chưa có kết quả cuối: đang gửi/chờ PayOS xử lý, hoặc gửi đi nhưng chưa biết kết quả. */
    private static final Set<PayosOrderStatus> UNSETTLED = Set.of(PayosOrderStatus.PENDING, PayosOrderStatus.SUBMITTED, PayosOrderStatus.UNKNOWN);

    private final PayosPayoutOrderRepository orderRepository;

    public boolean hasUnsettled(PayosOrderKind kind, Long entityId) {
        return orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(kind, entityId)
                .map(o -> UNSETTLED.contains(o.getStatus())).orElse(false);
    }

    /** Ném lỗi nếu khoản đang có lệnh PayOS chưa chốt: thao tác tay lúc đó có thể làm tiền đi hai lần. */
    public void assertNoUnsettled(PayosOrderKind kind, Long entityId) {
        orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(kind, entityId).ifPresent(o -> {
            if (UNSETTLED.contains(o.getStatus())) {
                throw new BusinessRuleViolationException("Đang có lệnh chi PayOS (" + o.getReferenceId()
                        + ") chưa có kết quả; hãy chờ kết quả (hoặc bấm gửi lại) thay vì thao tác tay để tránh chuyển tiền hai lần");
            }
        });
    }
}
