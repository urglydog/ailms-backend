package com.lms.report.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Việc nền báo cáo định kỳ: mỗi giờ kiểm tra các báo cáo đến hạn (xem {@link FinanceReportService#runScheduled}). Dùng vòng kiểm theo giờ thay vì cron cố định
 * để nếu server tắt đúng giờ gửi thì lần chạy kế tiếp vẫn bù; trùng lặp được chặn bằng dedupe_key. Lỗi không bao giờ ném ra ngoài.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceReportJob {

    private final FinanceReportService reportService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    @SchedulerLock(name = "FinanceReportJob.run", lockAtMostFor = "PT1H", lockAtLeastFor = "PT55M")
    public void run() {
        try {
            int n = reportService.runScheduled(LocalDateTime.now());
            if (n > 0) {
                log.info("Đã tạo {} báo cáo tài chính định kỳ", n);
            }
        } catch (RuntimeException e) {
            log.error("Lỗi tạo báo cáo tài chính định kỳ: {}", e.getMessage());
        }
    }
}
