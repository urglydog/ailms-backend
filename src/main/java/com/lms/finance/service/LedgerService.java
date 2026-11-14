package com.lms.finance.service;

import com.lms.common.enums.LedgerEntryType;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.entity.InstructorLedgerEntry;
import com.lms.finance.repository.InstructorLedgerEntryRepository;
import com.lms.payment.entity.Payment;
import java.math.BigDecimal;
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

    /**
     * Trừ lại thu nhập khi đơn bị hoàn tiền (11/11/2026, Giai đoạn B): thêm dòng REFUND_REVERSAL âm bằng
     * {@code charge}. {@code available_at} thừa hưởng của dòng EARNING nếu nó chưa "chín" → 2 dòng cùng
     * nằm ở mục "đang giữ" rồi triệt tiêu nhau, giảng viên không bao giờ thấy khoản này trong số dư khả
     * dụng. Nếu EARNING đã chín/đã chi → dòng âm có hiệu lực ngay (số dư có thể âm, bù trừ ở khoản kế).
     * Idempotent theo UNIQUE (payment_id, entry_type). {@code charge <= 0} (nền tảng bù) không ghi gì.
     */
    @Transactional
    public void recordRefundReversal(Payment payment, java.math.BigDecimal charge, Long actorUserId) {
        if (charge == null || charge.signum() <= 0) {
            return;
        }
        if (repository.existsByPayment_IdAndEntryType(payment.getId(), LedgerEntryType.REFUND_REVERSAL)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        var earning = repository.findByPayment_IdAndEntryType(payment.getId(), LedgerEntryType.EARNING);
        LocalDateTime availableAt = earning.map(InstructorLedgerEntry::getAvailableAt)
                .filter(at -> at.isAfter(now)).orElse(now);

        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(payment.getCourse().getInstructor());
        entry.setEntryType(LedgerEntryType.REFUND_REVERSAL);
        entry.setAmount(charge.negate());
        entry.setAvailableAt(availableAt);
        entry.setPayment(payment);
        entry.setRefEntryId(earning.map(InstructorLedgerEntry::getId).orElse(null));
        entry.setCreatedByUserId(actorUserId);
        entry.setNote("Hoàn tiền đơn " + payment.getTxnRef());
        repository.save(entry);
    }

    /** Số dư của giảng viên (11/11/2026, Giai đoạn C). {@code withdrawable = max(0, available - frozen)}. */
    public record Balance(BigDecimal available, BigDecimal pending, BigDecimal frozen, BigDecimal withdrawable) {}

    @Transactional(readOnly = true)
    public Balance getBalance(Long instructorId) {
        // +2s: DATETIME của MySQL làm tròn giây, dòng vừa ghi (giữ chỗ rút, hoàn tiền) có available_at = "bây giờ"
        // có thể bị làm tròn lên 1s trong tương lai → bù để dòng đó luôn được tính ngay.
        LocalDateTime now = LocalDateTime.now().plusSeconds(2);
        BigDecimal available = repository.sumAvailable(instructorId, now);
        BigDecimal pending = repository.sumPending(instructorId, now);
        BigDecimal frozen = repository.sumFrozen(instructorId, now);
        BigDecimal withdrawable = available.subtract(frozen).max(BigDecimal.ZERO);
        return new Balance(available, pending, frozen, withdrawable);
    }

    /** Giữ chỗ số tiền giảng viên yêu cầu rút (dòng âm, hiệu lực ngay). Idempotent theo payout. */
    @Transactional
    public void recordPayoutReserve(com.lms.auth.entity.User instructor, BigDecimal amount, Long payoutId, String code) {
        if (repository.existsByPayoutIdAndEntryType(payoutId, LedgerEntryType.PAYOUT_RESERVE)) {
            return;
        }
        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(instructor);
        entry.setEntryType(LedgerEntryType.PAYOUT_RESERVE);
        entry.setAmount(amount.negate());
        entry.setAvailableAt(LocalDateTime.now());
        entry.setPayoutId(payoutId);
        entry.setNote("Giữ chỗ yêu cầu rút " + code);
        repository.save(entry);
    }

    /** Trả lại khoản giữ chỗ khi yêu cầu rút bị hủy/từ chối/thất bại (dòng dương). Idempotent theo payout. */
    @Transactional
    public void recordPayoutRelease(com.lms.auth.entity.User instructor, BigDecimal amount, Long payoutId, String code) {
        if (repository.existsByPayoutIdAndEntryType(payoutId, LedgerEntryType.PAYOUT_RELEASE)) {
            return;
        }
        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(instructor);
        entry.setEntryType(LedgerEntryType.PAYOUT_RELEASE);
        entry.setAmount(amount);
        entry.setAvailableAt(LocalDateTime.now());
        entry.setPayoutId(payoutId);
        entry.setNote("Trả lại khoản giữ chỗ yêu cầu rút " + code);
        repository.save(entry);
    }

    /** Ghi một lần trả nợ qua PayOS (dòng dương, hiệu lực ngay). Idempotent theo {@code debtPaymentId}. */
    @Transactional
    public void recordDebtRepayment(com.lms.auth.entity.User instructor, BigDecimal amount, Long debtPaymentId) {
        if (repository.existsByDebtPaymentId(debtPaymentId)) {
            return;
        }
        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(instructor);
        entry.setEntryType(LedgerEntryType.DEBT_REPAYMENT);
        entry.setAmount(amount);
        entry.setAvailableAt(LocalDateTime.now());
        entry.setDebtPaymentId(debtPaymentId);
        entry.setNote("Trả nợ qua PayOS");
        repository.save(entry);
    }

    /** Điều chỉnh tay có lý do (admin xóa nợ, hoàn lại phần phạt khi khiếu nại thành công...). Số tiền có thể dương hoặc âm. */
    @Transactional
    public void recordAdjustment(com.lms.auth.entity.User instructor, BigDecimal amount, String note, Long actorUserId) {
        InstructorLedgerEntry entry = new InstructorLedgerEntry();
        entry.setInstructor(instructor);
        entry.setEntryType(LedgerEntryType.ADJUSTMENT);
        entry.setAmount(amount);
        entry.setAvailableAt(LocalDateTime.now());
        entry.setCreatedByUserId(actorUserId);
        entry.setNote(note == null ? null : (note.length() > 255 ? note.substring(0, 255) : note));
        repository.save(entry);
    }

    /** Các dòng sổ cái gần đây của giảng viên (sao kê). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InstructorLedgerEntry> getStatement(Long instructorId, int page, int size) {
        return repository.findByInstructor_IdOrderByCreatedAtDescIdDesc(instructorId,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
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
