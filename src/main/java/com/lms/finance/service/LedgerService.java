package com.lms.finance.service;

import com.lms.common.enums.LedgerEntryType;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.entity.InstructorLedgerEntry;
import com.lms.finance.repository.InstructorLedgerEntryRepository;
import com.lms.payment.entity.Payment;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ cái thu nhập giảng viên (11/11/2026, Epic hoàn tiền – chi trả). Giai đoạn A chỉ ghi dòng
 * EARNING khi đơn PAID; hoàn tiền (REFUND_REVERSAL) và rút tiền (PAYOUT_*) bổ sung ở Giai đoạn
 * B/C. Chạy TRONG transaction của người gọi → đơn PAID và dòng sổ cái cùng commit/rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final InstructorLedgerEntryRepository repository;
    private final SystemConfigService systemConfigService;

    /**
     * Ghi thu nhập của 1 đơn vừa PAID. Idempotent: gọi lại cho cùng đơn không tạo dòng thứ hai
     * (kiểm tra trước + UNIQUE ở DB). Đơn không có thu nhập (0đ) bị bỏ qua.
     */
    @Transactional
    public void recordEarning(Payment payment) {
        if (payment.getInstructorEarning() == null || payment.getInstructorEarning().signum() <= 0) {
            return;
        }
        if (payment.getId() != null
                && repository.existsByPayment_IdAndEntryType(payment.getId(), LedgerEntryType.EARNING)) {
            log.info("Ledger EARNING đã tồn tại cho payment {}", payment.getTxnRef());
            return;
        }
        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();

        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(payment.getCourse().getInstructor());
        entry.setEntryType(LedgerEntryType.EARNING);
        entry.setAmount(payment.getInstructorEarning());
        entry.setAvailableAt(paidAt.plusDays(holdDays()));
        entry.setPayment(payment);
        entry.setNote("Thu nhập từ đơn " + payment.getTxnRef());
        repository.save(entry);
    }

    /** Số ngày giữ tiền = hạn hoàn tiền + thời gian giữ thêm (cấu hình admin). */
    int holdDays() {
        int window = systemConfigService.getInt(
                FinanceConfigKeys.REFUND_WINDOW_DAYS, FinanceConfigKeys.DEFAULT_REFUND_WINDOW_DAYS);
        int extra = systemConfigService.getInt(
                FinanceConfigKeys.PAYOUT_HOLD_EXTRA_DAYS, FinanceConfigKeys.DEFAULT_PAYOUT_HOLD_EXTRA_DAYS);
        return window + extra;
    }
}
