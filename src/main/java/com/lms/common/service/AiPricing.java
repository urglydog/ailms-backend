package com.lms.common.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quy đổi mức dùng AI thành chi phí ước tính (USD). Đơn giá văn bản phụ thuộc MODEL nên có bảng giá theo model (dưới đây, theo bảng giá công khai của Google; đối chiếu với hóa đơn
 * Google AI Studio của dự án 21/11/2026); model không có trong bảng dùng đơn giá dự phòng ở Cài đặt → Chi phí AI. Token "suy nghĩ" của Gemini tính cùng đơn giá token đầu ra.
 * Đọc cấu hình lỗi/thiếu thì dùng giá trị mặc định, không làm vỡ việc ghi nhận.
 */
@Component
@RequiredArgsConstructor
public class AiPricing {

    static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    /** Giá khuyến mãi ra mắt của Gemini 3.6 Flash kết thúc hết 31/12/2026; từ 1/1/2027 áp giá tiêu chuẩn. */
    static final java.time.LocalDate GEMINI_36_INTRO_END = java.time.LocalDate.of(2026, 12, 31);

    /** Đơn giá (USD / 1 triệu token) đầu vào và đầu ra. */
    record TextPrice(BigDecimal in, BigDecimal out) {}

    private final SystemConfigService config;

    /** Bảng giá theo model; null nếu model không có trong bảng. Thứ tự kiểm tra: tên cụ thể (flash-lite) trước tên chung. */
    static TextPrice knownPrice(String model, java.time.LocalDate on) {
        if (model == null) return null;
        String m = model.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("flash-lite")) return new TextPrice(new BigDecimal("0.30"), new BigDecimal("2.50"));
        if (m.contains("3.6-flash")) {
            return on != null && on.isAfter(GEMINI_36_INTRO_END)
                    ? new TextPrice(new BigDecimal("1.50"), new BigDecimal("7.50"))
                    : new TextPrice(new BigDecimal("0.75"), new BigDecimal("3.75"));
        }
        if (m.contains("3.5-flash")) return new TextPrice(new BigDecimal("1.50"), new BigDecimal("9.00"));
        return null;
    }

    public BigDecimal textCost(long promptTokens, long completionTokens, long thinkingTokens) {
        return textCost(null, null, promptTokens, completionTokens, thinkingTokens);
    }

    /** Chi phí văn bản theo model; {@code on} là ngày phát sinh (để áp đúng giá khuyến mãi theo thời điểm), null = hôm nay. */
    public BigDecimal textCost(String model, java.time.LocalDate on, long promptTokens, long completionTokens, long thinkingTokens) {
        TextPrice known = knownPrice(model, on == null ? java.time.LocalDate.now() : on);
        BigDecimal in = known != null ? known.in() : config.getDecimal("AI_PRICE_INPUT_USD_PER_M", new BigDecimal("0.75"));
        BigDecimal out = known != null ? known.out() : config.getDecimal("AI_PRICE_OUTPUT_USD_PER_M", new BigDecimal("3.75"));
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
