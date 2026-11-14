package com.lms.finance.debt;

import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.entity.InstructorDebtState;
import com.lms.finance.repository.InstructorDebtStateRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cổng chặn theo bậc công nợ (14/11/2026): bậc 2 (nợ quá {@code DEBT_RESTRICT_DAYS} ngày) không xuất bản khóa mới/không đăng ký quảng bá;
 * bậc 3 (quá {@code DEBT_SUSPEND_DAYS} ngày) khóa tạm ngừng bán mới. Đang có khiếu nại hoàn tiền chờ xử lý thì chế tài tạm dừng (tối đa
 * bậc 1). Chỉ đọc 1 dòng trạng thái nên rẻ, gọi được ở đường nóng (thanh toán).
 */
@Component
@RequiredArgsConstructor
public class DebtGuard {

    private final InstructorDebtStateRepository stateRepository;
    private final PaymentRefundRepository refundRepository;
    private final SystemConfigService config;

    /** 0 = không nợ, 1 = đang nợ, 2 = hạn chế, 3 = ngừng bán mới. */
    public int levelOf(Long instructorId) {
        if (instructorId == null) {
            return 0;
        }
        return stateRepository.findById(instructorId).map(s -> effectiveLevel(s, LocalDateTime.now())).orElse(0);
    }

    int effectiveLevel(InstructorDebtState state, LocalDateTime now) {
        int level = levelFor(state.getDebtSince(), now);
        if (level >= 2 && refundRepository.existsOpenAppealByInstructor(state.getInstructorId())) {
            return 1;
        }
        return level;
    }

    int levelFor(LocalDateTime debtSince, LocalDateTime now) {
        long days = ChronoUnit.DAYS.between(debtSince, now);
        if (days >= suspendDays()) {
            return 3;
        }
        if (days >= restrictDays()) {
            return 2;
        }
        return 1;
    }

    public int restrictDays() {
        return config.getInt(FinanceConfigKeys.DEBT_RESTRICT_DAYS, 30);
    }

    public int suspendDays() {
        return config.getInt(FinanceConfigKeys.DEBT_SUSPEND_DAYS, 90);
    }

    /** Xuất bản khóa (gửi duyệt). */
    public void assertCanPublish(Long instructorId) {
        if (levelOf(instructorId) >= 2) {
            throw new BusinessRuleViolationException("Bạn đang có công nợ quá " + restrictDays()
                    + " ngày nên chưa thể xuất bản khóa mới. Hãy trả nợ ở trang Thu nhập để tiếp tục.");
        }
    }

    /** Đăng ký quảng bá. */
    public void assertCanPromote(Long instructorId) {
        if (levelOf(instructorId) >= 2) {
            throw new BusinessRuleViolationException("Bạn đang có công nợ quá " + restrictDays()
                    + " ngày nên chưa thể đăng ký quảng bá. Hãy trả nợ ở trang Thu nhập.");
        }
    }

    /** Mua khóa của giảng viên này (chặn bán mới, học viên đã mua vẫn học bình thường). */
    public void assertCanSell(Long instructorId) {
        if (levelOf(instructorId) >= 3) {
            throw new BusinessRuleViolationException("Khóa học này tạm ngừng nhận đăng ký mới. Vui lòng quay lại sau.");
        }
    }

    /** Các giảng viên đang ở bậc ngừng bán mới (ẩn khỏi kệ nổi bật). */
    public List<Long> suspendedInstructorIds() {
        List<Long> ids = stateRepository.findInstructorIdsInDebtBefore(LocalDateTime.now().minusDays(suspendDays()));
        return ids.stream().filter(id -> !refundRepository.existsOpenAppealByInstructor(id)).toList();
    }
}
