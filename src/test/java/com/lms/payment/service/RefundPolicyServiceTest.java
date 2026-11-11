package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.lms.common.enums.DisputeCategory;
import com.lms.common.enums.DisputeEligibility;
import com.lms.common.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Mốc ngày dùng trong test (2026): 03/11 Thứ Ba, 04/11 Thứ Tư, 06/11 Thứ Sáu, 07/11 Thứ Bảy, 11/11 Thứ Tư. */
@ExtendWith(MockitoExtension.class)
class RefundPolicyServiceTest {

    @Mock private SystemConfigService systemConfigService;

    @InjectMocks
    private RefundPolicyService service;

    @BeforeEach
    void setUp() {
        lenient().when(systemConfigService.getInt("REFUND_WINDOW_DAYS", 7)).thenReturn(7);
        lenient().when(systemConfigService.getInt("REFUND_MAX_PROGRESS_PCT", 20)).thenReturn(20);
        lenient().when(systemConfigService.getString("REFUND_HOLIDAYS", "")).thenReturn("");
    }

    @Test
    void deadline_weekdayLandsOnSameDay_endOfDay() {
        // Thứ Tư 04/11 + 7 ngày = Thứ Tư 11/11
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 4, 10, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 23, 59, 59));
        // Thứ Sáu 06/11 + 7 ngày = Thứ Sáu 13/11
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 6, 8, 30)))
                .isEqualTo(LocalDateTime.of(2026, 11, 13, 23, 59, 59));
    }

    @Test
    void deadline_fallingOnWeekend_extendsToEndOfNextMonday() {
        // Thứ Bảy 07/11 + 7 ngày = Thứ Bảy 14/11 → hết Thứ Hai 16/11
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 7, 9, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
        // Chủ Nhật 08/11 + 7 = Chủ Nhật 15/11 → hết Thứ Hai 16/11
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 8, 9, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
    }

    @Test
    void deadline_fallingOnHolidays_skipsThemAndTheWeekendThatFollows() {
        // Thứ Ba 03/11 + 7 = Thứ Ba 10/11; 10/11 và 11/11 là lễ → hết Thứ Năm 12/11
        lenient().when(systemConfigService.getString("REFUND_HOLIDAYS", "")).thenReturn("2026-11-10, 2026-11-11");
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 3, 9, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 12, 23, 59, 59));
    }

    @Test
    void deadline_ignoresMalformedHolidayEntries() {
        lenient().when(systemConfigService.getString("REFUND_HOLIDAYS", "")).thenReturn("không-phải-ngày,2026-11-10");
        assertThat(service.refundDeadline(LocalDateTime.of(2026, 11, 3, 9, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 23, 59, 59));
    }

    @Test
    void evaluate_inWindowAndLowProgress_isAutoEligible_includingExactThreshold() {
        LocalDateTime paid = LocalDateTime.of(2026, 11, 4, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 0);
        assertThat(service.evaluate(DisputeCategory.CHANGED_MIND, paid, new BigDecimal("20"), now))
                .isEqualTo(DisputeEligibility.AUTO_ELIGIBLE);
        assertThat(service.evaluate(DisputeCategory.CHANGED_MIND, paid, BigDecimal.ZERO, now))
                .isEqualTo(DisputeEligibility.AUTO_ELIGIBLE);
        assertThat(service.evaluate(DisputeCategory.CHANGED_MIND, paid, null, now))
                .isEqualTo(DisputeEligibility.AUTO_ELIGIBLE);
    }

    @Test
    void evaluate_progressAboveThreshold_orPastDeadline_isOutOfPolicy() {
        LocalDateTime paid = LocalDateTime.of(2026, 11, 4, 10, 0);
        assertThat(service.evaluate(DisputeCategory.CHANGED_MIND, paid, new BigDecimal("20.01"),
                LocalDateTime.of(2026, 11, 9, 10, 0))).isEqualTo(DisputeEligibility.OUT_OF_POLICY);
        // đúng hạn chót vẫn được, quá 1 giây thì không
        assertThat(service.evaluate(DisputeCategory.OTHER, paid, BigDecimal.ZERO,
                LocalDateTime.of(2026, 11, 11, 23, 59, 59))).isEqualTo(DisputeEligibility.AUTO_ELIGIBLE);
        assertThat(service.evaluate(DisputeCategory.OTHER, paid, BigDecimal.ZERO,
                LocalDateTime.of(2026, 11, 12, 0, 0, 0))).isEqualTo(DisputeEligibility.OUT_OF_POLICY);
        assertThat(service.evaluate(DisputeCategory.OTHER, null, BigDecimal.ZERO, LocalDateTime.of(2026, 11, 9, 10, 0)))
                .isEqualTo(DisputeEligibility.OUT_OF_POLICY);
    }

    @Test
    void evaluate_technicalCategories_bypassWindowAndProgress() {
        LocalDateTime paid = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 0);
        for (DisputeCategory c : new DisputeCategory[]{
                DisputeCategory.TECHNICAL_ERROR, DisputeCategory.DOUBLE_CHARGE, DisputeCategory.NOT_ENROLLED}) {
            assertThat(service.evaluate(c, paid, new BigDecimal("100"), now)).isEqualTo(DisputeEligibility.EXCEPTION);
        }
    }
}
