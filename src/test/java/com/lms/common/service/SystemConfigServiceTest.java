package com.lms.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.common.entity.SystemConfig;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.repository.SystemConfigRepository;
import com.lms.finance.service.FinanceAuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository repository;
    @Mock private FinanceAuditService financeAuditService;

    @InjectMocks
    private SystemConfigService service;

    private static SystemConfig cfg(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    @Test
    void typedGetters_readFromRepositoryAndCacheForSubsequentCalls() {
        when(repository.findAll()).thenReturn(List.of(
                cfg("REFUND_WINDOW_DAYS", "10"), cfg("REVENUE_ORGANIC_INSTRUCTOR_PCT", "70.5"), cfg("X", "abc")));

        assertThat(service.getInt("REFUND_WINDOW_DAYS", 7)).isEqualTo(10);
        assertThat(service.getDecimal("REVENUE_ORGANIC_INSTRUCTOR_PCT", BigDecimal.ONE)).isEqualByComparingTo("70.5");
        assertThat(service.getString("X", "d")).isEqualTo("abc");

        verify(repository, times(1)).findAll(); // 3 lần đọc, chỉ 1 lần chạm DB (cache 60s)
    }

    @Test
    void typedGetters_missingOrMalformed_fallBackToDefault() {
        when(repository.findAll()).thenReturn(List.of(cfg("REFUND_WINDOW_DAYS", "không phải số")));

        assertThat(service.getInt("REFUND_WINDOW_DAYS", 7)).isEqualTo(7);
        assertThat(service.getDecimal("REFUND_WINDOW_DAYS", new BigDecimal("5"))).isEqualByComparingTo("5");
        assertThat(service.getInt("KHONG_TON_TAI", 3)).isEqualTo(3);
        assertThat(service.getString("KHONG_TON_TAI", "mặc định")).isEqualTo("mặc định");
    }

    @Test
    void typedGetters_whenDatabaseFails_returnDefaultInsteadOfThrowing() {
        when(repository.findAll()).thenThrow(new IllegalStateException("db down"));

        assertThat(service.getInt("REFUND_WINDOW_DAYS", 7)).isEqualTo(7);
    }

    @Test
    void updateValues_validChange_savesAuditsAndInvalidatesCache() {
        SystemConfig c = cfg("REVENUE_ORGANIC_INSTRUCTOR_PCT", "37");
        when(repository.findByConfigKey("REVENUE_ORGANIC_INSTRUCTOR_PCT")).thenReturn(Optional.of(c));
        when(repository.findAll()).thenReturn(List.of(c));

        service.updateValues(Map.of("REVENUE_ORGANIC_INSTRUCTOR_PCT", " 70 "), "admin@lms.local");

        assertThat(c.getConfigValue()).isEqualTo("70");
        verify(financeAuditService).record(eq("admin@lms.local"), eq("CONFIG_UPDATED"), eq("system_config"),
                eq("REVENUE_ORGANIC_INSTRUCTOR_PCT"), eq(Map.of("value", "37")), eq(Map.of("value", "70")));
    }

    @Test
    void updateValues_unchangedValue_writesNoAuditLine() {
        SystemConfig c = cfg("REFUND_WINDOW_DAYS", "7");
        when(repository.findByConfigKey("REFUND_WINDOW_DAYS")).thenReturn(Optional.of(c));
        when(repository.findAll()).thenReturn(List.of(c));

        service.updateValues(Map.of("REFUND_WINDOW_DAYS", "7"), "admin@lms.local");

        verify(financeAuditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateValues_invalidFinanceValue_rejectsWholeBatchWithoutChangingAnything() {
        SystemConfig good = cfg("REFUND_WINDOW_DAYS", "7");
        SystemConfig bad = cfg("REVENUE_ORGANIC_INSTRUCTOR_PCT", "37");
        when(repository.findByConfigKey("REFUND_WINDOW_DAYS")).thenReturn(Optional.of(good));
        when(repository.findByConfigKey("REVENUE_ORGANIC_INSTRUCTOR_PCT")).thenReturn(Optional.of(bad));

        assertThatThrownBy(() -> service.updateValues(
                Map.of("REFUND_WINDOW_DAYS", "10", "REVENUE_ORGANIC_INSTRUCTOR_PCT", "150"), "admin@lms.local"))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(good.getConfigValue()).isEqualTo("7");
        assertThat(bad.getConfigValue()).isEqualTo("37");
        verify(financeAuditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateValues_unknownKeyIsIgnored_nullMapIsSafe() {
        when(repository.findByConfigKey("KHONG_TON_TAI")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());

        service.updateValues(Map.of("KHONG_TON_TAI", "1"), "admin@lms.local");
        service.updateValues(null, "admin@lms.local");

        verify(financeAuditService, never()).record(any(), any(), any(), any(), any(), any());
    }
}
