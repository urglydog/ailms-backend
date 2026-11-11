package com.lms.payment.service;

import com.lms.common.enums.DisputeCategory;
import com.lms.common.enums.DisputeEligibility;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Chính sách hoàn tiền (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn B) — logic thuần, đọc tham số từ
 * cấu hình admin ({@code REFUND_WINDOW_DAYS}, {@code REFUND_MAX_PROGRESS_PCT}, {@code REFUND_HOLIDAYS}).
 *
 * <p>Hạn hoàn = hết ngày thứ N kể từ ngày thanh toán; nếu ngày đó rơi vào T7/CN hoặc ngày lễ thì dời sang
 * hết ngày làm việc kế tiếp (phủ cuối tuần/lễ). Kết quả chỉ để GỢI Ý — admin vẫn quyết định cuối.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundPolicyService {

    private static final Set<DisputeCategory> BYPASS_WINDOW = Set.of(
            DisputeCategory.TECHNICAL_ERROR, DisputeCategory.DOUBLE_CHARGE, DisputeCategory.NOT_ENROLLED);

    private final SystemConfigService systemConfigService;

    /** Thời điểm cuối cùng được xin hoàn tiền cho đơn thanh toán lúc {@code paidAt}. */
    public LocalDateTime refundDeadline(LocalDateTime paidAt) {
        int windowDays = systemConfigService.getInt(
                FinanceConfigKeys.REFUND_WINDOW_DAYS, FinanceConfigKeys.DEFAULT_REFUND_WINDOW_DAYS);
        Set<LocalDate> holidays = holidays();
        LocalDate date = paidAt.toLocalDate().plusDays(windowDays);
        // Dừng chắc chắn: tối đa vài chục ngày liên tiếp không làm việc (danh sách lễ tối đa ~20 ngày).
        for (int guard = 0; guard < 60 && isNonWorkingDay(date, holidays); guard++) {
            date = date.plusDays(1);
        }
        return date.atTime(LocalTime.MAX.withNano(0));
    }

    /**
     * Đánh giá điều kiện hoàn tiền tại thời điểm gửi khiếu nại.
     * <ul>
     *   <li>Lý do kỹ thuật (lỗi hệ thống/thanh toán trùng/không được ghi danh) → {@code EXCEPTION}, bỏ qua hạn và tiến độ.</li>
     *   <li>Còn trong hạn VÀ tiến độ học ≤ ngưỡng → {@code AUTO_ELIGIBLE}.</li>
     *   <li>Ngược lại → {@code OUT_OF_POLICY}.</li>
     * </ul>
     */
    public DisputeEligibility evaluate(DisputeCategory category, LocalDateTime paidAt,
                                       BigDecimal progressPct, LocalDateTime now) {
        if (category != null && BYPASS_WINDOW.contains(category)) {
            return DisputeEligibility.EXCEPTION;
        }
        if (paidAt == null) {
            return DisputeEligibility.OUT_OF_POLICY;
        }
        BigDecimal maxProgress = BigDecimal.valueOf(systemConfigService.getInt(FinanceConfigKeys.REFUND_MAX_PROGRESS_PCT, 20));
        BigDecimal progress = progressPct == null ? BigDecimal.ZERO : progressPct;
        boolean inWindow = !now.isAfter(refundDeadline(paidAt));
        boolean lowProgress = progress.compareTo(maxProgress) <= 0;
        return inWindow && lowProgress ? DisputeEligibility.AUTO_ELIGIBLE : DisputeEligibility.OUT_OF_POLICY;
    }

    private static boolean isNonWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY || holidays.contains(date);
    }

    private Set<LocalDate> holidays() {
        String raw = systemConfigService.getString(FinanceConfigKeys.REFUND_HOLIDAYS, "");
        Set<LocalDate> result = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            try {
                result.add(LocalDate.parse(part.trim()));
            } catch (DateTimeParseException e) {
                log.warn("Bỏ qua ngày lễ không hợp lệ trong REFUND_HOLIDAYS: \"{}\"", part.trim());
            }
        }
        return result;
    }
}
