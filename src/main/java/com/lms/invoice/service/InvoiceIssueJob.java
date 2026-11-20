package com.lms.invoice.service;

import com.lms.invoice.entity.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Việc nền phát hành chứng từ (20/11/2026): cứ 2 phút quét các giao dịch đã hoàn tất mà chưa có chứng từ rồi phát hành. Dùng CHUNG một đường
 * cho chứng từ mới và phát bù/backfill giao dịch cũ, và cố ý KHÔNG gắn vào luồng thanh toán/hoàn tiền/rút tiền/trả nợ để lỗi phát hành
 * không bao giờ làm vỡ webhook PayOS hay giao dịch tiền. Mỗi lần xử lý tối đa {@link #BATCH} dòng mỗi loại (server nhỏ); từng chứng từ
 * nằm trong transaction riêng nên một ca lỗi không chặn các ca còn lại. Hệ thống một instance (không có ShedLock) nên không chạy song song.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceIssueJob {

    static final int BATCH = 100;

    private final InvoiceSourceFinder finder;
    private final InvoiceIssueService issueService;
    private final InvoiceMailer mailer;

    @Scheduled(fixedDelay = 120_000, initialDelay = 180_000)
    public void run() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("Lỗi phát hành chứng từ: {}", e.getMessage());
        }
    }

    /** Trả tổng số chứng từ đã phát hành trong lượt này. Biên lai chạy trước để phiếu hoàn gắn được với biên lai gốc. */
    int runOnce() {
        int n = 0;
        n += issueAll(finder.paymentsWithoutReceipt(BATCH), issueService::issueReceipt, "biên lai");
        n += issueAll(finder.refundsWithoutVoucher(BATCH), issueService::issueRefund, "phiếu hoàn tiền");
        n += issueAll(finder.payoutsWithoutVoucher(BATCH), issueService::issuePayout, "phiếu chi");
        n += issueAll(finder.debtPaymentsWithoutReceipt(BATCH), issueService::issueDebtReceipt, "biên lai công nợ");
        if (n > 0) {
            log.info("Đã phát hành {} chứng từ", n);
        }
        return n;
    }

    private <T> int issueAll(List<T> sources, Function<T, Optional<Invoice>> issuer, String label) {
        int n = 0;
        for (T source : sources) {
            try {
                Optional<Invoice> issued = issuer.apply(source);
                if (issued.isPresent()) {
                    n++;
                    mailer.sendIfRecent(issued.get().getId());
                }
            } catch (RuntimeException e) {
                log.warn("Không phát hành được {}: {}", label, e.getMessage());
            }
        }
        return n;
    }
}
