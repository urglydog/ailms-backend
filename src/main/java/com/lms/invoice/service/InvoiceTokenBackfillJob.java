package com.lms.invoice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.repository.InvoiceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Cấp mã truy cập (cho liên kết/QR) cho các chứng từ phát hành trước khi có tính năng này; chạy theo lô nhỏ, mỗi chứng từ một transaction, idempotent. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceTokenBackfillJob {

    static final int BATCH = 100;

    private final InvoiceRepository invoiceRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    @SchedulerLock(name = "InvoiceTokenBackfillJob.run", lockAtMostFor = "PT15M", lockAtLeastFor = "PT9M30S")
    public void run() {
        try {
            int n = backfill();
            if (n > 0) {
                log.info("Đã cấp mã truy cập cho {} chứng từ cũ", n);
            }
        } catch (RuntimeException e) {
            log.error("Lỗi cấp mã truy cập chứng từ: {}", e.getMessage());
        }
    }

    int backfill() {
        List<Invoice> missing = invoiceRepository.findByAccessTokenIsNull(PageRequest.of(0, BATCH));
        int done = 0;
        for (Invoice i : missing) {
            Integer ok = transactionTemplate.execute(status -> {
                Invoice fresh = invoiceRepository.findById(i.getId()).orElse(null);
                if (fresh == null || fresh.getAccessToken() != null) {
                    return 0;
                }
                fresh.setAccessToken(InvoiceIssueService.newAccessToken());
                invoiceRepository.save(fresh);
                return 1;
            });
            done += ok == null ? 0 : ok;
        }
        return done;
    }
}
