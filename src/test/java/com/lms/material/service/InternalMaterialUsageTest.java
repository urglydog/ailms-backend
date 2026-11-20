package com.lms.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.common.entity.AiUsageLog;
import com.lms.common.enums.MaterialType;
import com.lms.common.repository.AiUsageLogRepository;
import com.lms.common.service.AiPricing;
import com.lms.material.dto.InternalMaterialDto.FinishReq;
import com.lms.material.dto.InternalMaterialDto.UsageMetadataDto;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.repository.MaterialGenerationRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Chi phí học liệu (mindmap/flashcard/quiz) phải gồm token suy nghĩ và tính giá theo model đã trả lời. */
@ExtendWith(MockitoExtension.class)
class InternalMaterialUsageTest {

    @Mock private MaterialGenerationRepository materialGenerationRepository;
    @Mock private AiUsageLogRepository aiUsageLogRepository;
    @Mock private AiPricing aiPricing;
    @InjectMocks private InternalMaterialService service;

    private MaterialGeneration generation() {
        User u = new User();
        u.setId(5L);
        MaterialGeneration g = new MaterialGeneration();
        g.setUser(u);
        g.setMaterialType(MaterialType.MINDMAP);
        return g;
    }

    @Test
    void finish_recordsThinkingTokensAndPricesByModel() {
        when(materialGenerationRepository.findById(1L)).thenReturn(Optional.of(generation()));
        when(aiPricing.textCost(eq("gemini-3.6-flash"), any(), eq(1_000_000L), eq(1_000_000L), eq(1_000_000L))).thenReturn(new BigDecimal("8.25"));

        service.finish(1L, new FinishReq("FAILED", "lỗi", null, null, null, new UsageMetadataDto(1_000_000, 1_000_000, 3_000_000, 1_000_000, "gemini-3.6-flash")));

        ArgumentCaptor<AiUsageLog> saved = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(aiUsageLogRepository).save(saved.capture());
        AiUsageLog log = saved.getValue();
        assertThat(log.getThinkingTokens()).isEqualTo(1_000_000);
        assertThat(log.getTotalTokens()).isEqualTo(3_000_000);
        assertThat(log.getModel()).isEqualTo("gemini-3.6-flash");
        // Giá được tính theo model đã trả lời và gồm cả token suy nghĩ (bảng giá được kiểm ở AiUsageTrackingTest).
        assertThat(log.getCostUsd()).isEqualByComparingTo(new BigDecimal("8.25"));
    }

    @Test
    void finish_oldWorkerWithoutNewFieldsStillWorks() {
        when(materialGenerationRepository.findById(2L)).thenReturn(Optional.of(generation()));
        when(aiPricing.textCost(eq(null), any(), eq(100L), eq(50L), eq(0L))).thenReturn(new BigDecimal("0.001"));

        service.finish(2L, new FinishReq("FAILED", "lỗi", null, null, null, new UsageMetadataDto(100, 50, 150, null, null)));

        ArgumentCaptor<AiUsageLog> saved = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(aiUsageLogRepository).save(saved.capture());
        assertThat(saved.getValue().getThinkingTokens()).isZero();
        assertThat(saved.getValue().getModel()).isNull();
    }
}
