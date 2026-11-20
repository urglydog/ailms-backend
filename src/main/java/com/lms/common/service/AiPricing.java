package com.lms.common.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quy đổi mức dùng AI thành chi phí ước tính (USD). Đơn giá nằm trong cấu hình (Cài đặt → Chi phí AI) vì phụ thuộc model và bảng giá hiện hành của nhà cung cấp.
 * Token "suy nghĩ" của Gemini tính cùng đơn giá token đầu ra. Đọc cấu hình lỗi/thiếu thì dùng giá trị mặc định, không làm vỡ việc ghi nhận.
 */
@Component
@RequiredArgsConstructor
public class AiPricing {

    static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private final SystemConfigService config;

    public BigDecimal textCost(long promptTokens, long completionTokens, long thinkingTokens) {
        BigDecimal in = config.getDecimal("AI_PRICE_INPUT_USD_PER_M", new BigDecimal("0.30"));
        BigDecimal out = config.getDecimal("AI_PRICE_OUTPUT_USD_PER_M", new BigDecimal("2.50"));
        return in.multiply(BigDecimal.valueOf(Math.max(0, promptTokens))).divide(MILLION, 8, RoundingMode.HALF_UP)
                .add(out.multiply(BigDecimal.valueOf(Math.max(0, completionTokens) + Math.max(0, thinkingTokens))).divide(MILLION, 8, RoundingMode.HALF_UP))
                .setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal embeddingCost(long tokens) {
        BigDecimal price = config.getDecimal("AI_PRICE_EMBED_USD_PER_M", new BigDecimal("0.15"));
        return price.multiply(BigDecimal.valueOf(Math.max(0, tokens))).divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal audioCost(long seconds) {
        BigDecimal perHour = config.getDecimal("AI_PRICE_AUDIO_USD_PER_HOUR", new BigDecimal("0.04"));
        return perHour.multiply(BigDecimal.valueOf(Math.max(0, seconds))).divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal usdToVndRate() {
        return config.getDecimal("USD_VND_RATE", BigDecimal.valueOf(25_500));
    }
}
