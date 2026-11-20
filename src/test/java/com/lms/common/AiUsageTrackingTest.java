package com.lms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.controller.InternalAiUsageController;
import com.lms.common.controller.InternalAiUsageController.UsageReq;
import com.lms.common.entity.AiUsageLog;
import com.lms.common.repository.AiUsageLogRepository;
import com.lms.common.service.AiPricing;
import com.lms.common.service.SystemConfigService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiUsageTrackingTest {

    @Mock private SystemConfigService config;
    @Mock private AiUsageLogRepository repository;
    @Mock private UserRepository userRepository;

    private AiPricing pricing;
    private InternalAiUsageController controller;

    @BeforeEach
    void setUp() {
        pricing = new AiPricing(config);
        controller = new InternalAiUsageController(repository, userRepository, pricing);
        org.mockito.Mockito.lenient().when(config.getDecimal(any(), any())).thenAnswer(i -> i.getArgument(1));
    }

    @Test
    void textCost_billsThinkingTokensAtTheOutputPrice() {
        // Model không có trong bảng giá → đơn giá dự phòng 0.75 / 3.75: 1M đầu vào × 0.75 + (1M đầu ra + 1M suy nghĩ) × 3.75 = 8.25
        assertThat(pricing.textCost(1_000_000, 1_000_000, 1_000_000)).isEqualByComparingTo("8.25");
        assertThat(pricing.textCost(0, 0, 0)).isEqualByComparingTo("0");
        assertThat(pricing.textCost(-5, -5, -5)).isEqualByComparingTo("0");
    }

    @Test
    void textCost_usesTheModelPriceTableAndTheIntroPriceEndsAtTheEndOf2026() {
        var d = java.time.LocalDate.of(2026, 11, 12);
        // 3.6 Flash giá khuyến mãi 0.75 / 3.75
        assertThat(pricing.textCost("gemini-3.6-flash", d, 1_000_000, 1_000_000, 0)).isEqualByComparingTo("4.50");
        // sau 31/12/2026 giá tiêu chuẩn 1.50 / 7.50
        assertThat(pricing.textCost("gemini-3.6-flash", java.time.LocalDate.of(2027, 1, 1), 1_000_000, 1_000_000, 0)).isEqualByComparingTo("9.00");
        // 3.5 Flash 1.50 / 9.00 (suy nghĩ tính như đầu ra)
        assertThat(pricing.textCost("gemini-3.5-flash", d, 1_000_000, 0, 1_000_000)).isEqualByComparingTo("10.50");
        // flash-lite 0.30 / 2.50 — phải được nhận ra trước tên "3.5-flash" chung
        assertThat(pricing.textCost("gemini-3.5-flash-lite", d, 1_000_000, 1_000_000, 0)).isEqualByComparingTo("2.80");
    }

    @Test
    void embeddingAndAudioCostsUseTheirOwnPrices() {
        assertThat(pricing.embeddingCost(2_000_000)).isEqualByComparingTo("0.30");
        assertThat(pricing.audioCost(3600)).isEqualByComparingTo("0.04");
        assertThat(pricing.usdToVndRate()).isEqualByComparingTo("25500");
    }

    @Test
    void record_savesTheUsageWithServerSideCostAndResolvesTheUserByEmail() {
        User u = new User();
        u.setId(9L);
        when(userRepository.findByEmail("gv@gmail.com")).thenReturn(Optional.of(u));

        var res = controller.record(new UsageReq("gemini", "DUBBING_TRANSLATE", "gemini-x", 1000L, 500L, 2000L, null, "gv@gmail.com", null, 3L, 7L, false, false));

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<AiUsageLog> saved = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(repository).save(saved.capture());
        AiUsageLog log = saved.getValue();
        assertThat(log.getUserId()).isEqualTo(9L);
        assertThat(log.getTotalTokens()).isEqualTo(3500);
        assertThat(log.getThinkingTokens()).isEqualTo(2000);
        assertThat(log.getCostUsd()).isEqualByComparingTo("0.010125");
        assertThat(log.getCourseId()).isEqualTo(3L);
    }

    @Test
    void record_rejectsUnknownProviderAndBadFeatureNames_andNeverSaves() {
        for (UsageReq bad : new UsageReq[] {
                new UsageReq("OPENAI", "TUTOR_CHAT", null, 1L, 1L, 0L, null, null, null, null, null, null, null),
                new UsageReq("GEMINI", "tutor chat", null, 1L, 1L, 0L, null, null, null, null, null, null, null),
                new UsageReq("GEMINI", "'; DROP TABLE users;--", null, 1L, 1L, 0L, null, null, null, null, null, null, null),
                new UsageReq("GEMINI", null, null, 1L, 1L, 0L, null, null, null, null, null, null, null),
                new UsageReq("GEMINI", "X", null, 1L, 1L, 0L, null, null, null, null, null, null, null)}) {
            assertThat(controller.record(bad).getStatusCode().is4xxClientError()).isTrue();
        }
        verify(repository, never()).save(any());
    }

    @Test
    void record_clampsAbsurdAndNegativeNumbers_andIgnoresAnUnknownUser() {
        controller.record(new UsageReq("GROQ", "DUBBING_TRANSCRIBE", null, -5L, 99_999_999_999L, null, 999_999L, "nobody@x.vn", null, null, null, true, false));

        ArgumentCaptor<AiUsageLog> saved = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(repository).save(saved.capture());
        AiUsageLog log = saved.getValue();
        assertThat(log.getPromptTokens()).isZero();
        assertThat(log.getCompletionTokens()).isEqualTo(20_000_000);
        assertThat(log.getAudioSeconds()).isEqualTo(86_400);
        assertThat(log.getUserId()).isNull();
        assertThat(log.getEstimated()).isTrue();
        assertThat(log.getCostUsd()).isEqualByComparingTo(new BigDecimal("0.960000"));
    }
}
