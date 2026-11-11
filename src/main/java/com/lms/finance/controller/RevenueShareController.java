package com.lms.finance.controller;

import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chính sách chia doanh thu/hoàn tiền/chi trả đang áp dụng (11/11/2026, Epic hoàn tiền – chi
 * trả) — để FE giảng viên hiển thị đúng số thay vì hard-code 37%/97%. CHỈ cho người đã đăng nhập
 * với vai trò giảng viên/admin (không công khai), chỉ đọc, không nhận tham số.
 */
@RestController
@RequestMapping("/api/v1/instructor/revenue-share")
@RequiredArgsConstructor
public class RevenueShareController {

    private final SystemConfigService systemConfigService;

    public record RevenueShareRes(
            BigDecimal organicInstructorPct,
            BigDecimal referralInstructorPct,
            int refundWindowDays,
            int holdDays,
            int minPayoutAmount,
            String payoutDaysOfMonth
    ) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    public ResponseEntity<RevenueShareRes> get() {
        int window = systemConfigService.getInt(
                FinanceConfigKeys.REFUND_WINDOW_DAYS, FinanceConfigKeys.DEFAULT_REFUND_WINDOW_DAYS);
        int extra = systemConfigService.getInt(
                FinanceConfigKeys.PAYOUT_HOLD_EXTRA_DAYS, FinanceConfigKeys.DEFAULT_PAYOUT_HOLD_EXTRA_DAYS);
        return ResponseEntity.ok(new RevenueShareRes(
                systemConfigService.getDecimal(FinanceConfigKeys.REVENUE_ORGANIC_INSTRUCTOR_PCT,
                        FinanceConfigKeys.DEFAULT_ORGANIC_INSTRUCTOR_PCT),
                systemConfigService.getDecimal(FinanceConfigKeys.REVENUE_REFERRAL_INSTRUCTOR_PCT,
                        FinanceConfigKeys.DEFAULT_REFERRAL_INSTRUCTOR_PCT),
                window,
                window + extra,
                systemConfigService.getInt(FinanceConfigKeys.PAYOUT_MIN_AMOUNT, 100_000),
                systemConfigService.getString(FinanceConfigKeys.PAYOUT_DAYS_OF_MONTH, "5,20")));
    }
}
