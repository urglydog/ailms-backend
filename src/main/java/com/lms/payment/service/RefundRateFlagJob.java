package com.lms.payment.service;

import com.lms.auth.repository.UserRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.payment.entity.RefundRateAlert;
import com.lms.payment.repository.PaymentRepository;
import com.lms.payment.repository.RefundRateAlertRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Tín hiệu cho admin (14/11/2026): khóa có tỷ lệ hoàn tiền cao trong 30 ngày. CHỈ cảnh báo để admin xem xét nội dung khóa — hệ thống không tự
 * trừ tiền, gỡ khóa hay đánh dấu giảng viên (người dùng báo cáo thực tế mới là nguồn phát hiện nội dung có vấn đề).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRateFlagJob {

    static final int WINDOW_DAYS = 30;

    private final PaymentRepository paymentRepository;
    private final RefundRateAlertRepository alertRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SystemConfigService config;

    @Scheduled(cron = "0 40 3 * * *")
    public void run() {
        try {
            int flagged = scan(LocalDateTime.now());
            if (flagged > 0) {
                log.info("Đã cảnh báo {} khóa có tỷ lệ hoàn tiền cao", flagged);
            }
        } catch (RuntimeException e) {
            log.warn("Quét tỷ lệ hoàn tiền lỗi: {}", e.getMessage());
        }
    }

    int scan(LocalDateTime now) {
        int minCount = config.getInt(FinanceConfigKeys.REFUND_FLAG_MIN_COUNT, 3);
        int ratePct = config.getInt(FinanceConfigKeys.REFUND_FLAG_RATE_PCT, 20);
        int flagged = 0;
        for (Object[] row : paymentRepository.refundRateByCourse(now.minusDays(WINDOW_DAYS), minCount)) {
            long courseId = ((Number) row[0]).longValue();
            long total = ((Number) row[1]).longValue();
            long refunded = ((Number) row[2]).longValue();
            if (total <= 0 || refunded * 100 < (long) ratePct * total) {
                continue;
            }
            RefundRateAlert previous = alertRepository.findById(courseId).orElse(null);
            if (previous != null && previous.getAlertedAt().isAfter(now.minusDays(WINDOW_DAYS))) {
                continue;
            }
            String title = courseRepository.findById(courseId).map(c -> c.getTitle()).orElse("#" + courseId);
            List<com.lms.auth.entity.User> admins = userRepository.findByRole(Role.ADMIN);
            admins.forEach(a -> notificationService.notify(a.getId(), "REFUND_RATE_HIGH", "Khóa có tỷ lệ hoàn tiền cao",
                    "Khóa \"" + title + "\": " + refunded + "/" + total + " đơn bị hoàn trong " + WINDOW_DAYS
                            + " ngày. Hãy xem xét nội dung khóa và các khiếu nại liên quan.", "/admin/transactions/disputes"));
            RefundRateAlert alert = previous != null ? previous : new RefundRateAlert();
            alert.setCourseId(courseId);
            alert.setAlertedAt(now);
            alertRepository.save(alert);
            flagged++;
        }
        return flagged;
    }
}
