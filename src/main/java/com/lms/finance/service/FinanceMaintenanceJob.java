package com.lms.finance.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Việc nền nhẹ của hệ thống chi trả (11/11/2026, Giai đoạn C):
 * <ul>
 *   <li>Mỗi 5 phút: hủy yêu cầu rút quá 30 phút chưa xác nhận email, trả lại khoản giữ chỗ.</li>
 *   <li>Mỗi đêm: đối soát — đơn PAID có thu nhập mà thiếu dòng sổ cái thì ghi bù (idempotent) và báo admin.</li>
 * </ul>
 * Cả hai chỉ chạm các dòng thỏa điều kiện (có index / giới hạn số dòng) nên không đáng kể với server nhỏ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceMaintenanceJob {

    /** Số đơn tối đa xử lý/lần đối soát — chặn việc đối soát một lượng lớn đơn cùng lúc trên server ít RAM. */
    static final int RECONCILE_BATCH = 200;

    private final PayoutService payoutService;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @SchedulerLock(name = "FinanceMaintenanceJob.maintenance", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void expireUnconfirmedPayouts() {
        try {
            int n = payoutService.expireUnconfirmed(LocalDateTime.now());
            if (n > 0) {
                log.info("Đã hủy {} yêu cầu rút tiền quá hạn xác nhận email", n);
            }
        } catch (RuntimeException e) {
            log.error("Lỗi khi hủy yêu cầu rút quá hạn: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 10 3 * * *")
    @SchedulerLock(name = "FinanceMaintenanceJob.reconcileLedger", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void reconcileLedger() {
        try {
            int healed = reconcileMissingEarnings();
            if (healed > 0) {
                userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(),
                        "FINANCE_RECONCILE", "Đối soát sổ cái: đã ghi bù " + healed + " dòng thu nhập",
                        "Có " + healed + " đơn đã thanh toán thiếu dòng sổ cái thu nhập và đã được ghi bù tự động.",
                        "/admin/transactions/payouts"));
            }
        } catch (RuntimeException e) {
            log.error("Lỗi đối soát sổ cái: {}", e.getMessage());
        }
    }

    /** Ghi bù dòng EARNING cho đơn PAID thiếu dòng (tối đa {@link #RECONCILE_BATCH}/lần). Trả số dòng đã ghi bù. */
    int reconcileMissingEarnings() {
        List<com.lms.payment.entity.Payment> missing = paymentRepository.findPaidMissingLedgerEarning(
                org.springframework.data.domain.PageRequest.of(0, RECONCILE_BATCH));
        missing.forEach(ledgerService::recordEarning);
        return missing.size();
    }
}
