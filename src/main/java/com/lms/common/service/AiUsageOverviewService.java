package com.lms.common.service;

import com.lms.common.exception.InvalidRequestException;
import com.lms.common.repository.AiUsageLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tổng quan chi phí AI cho admin: tổng kỳ + kỳ liền trước (cùng độ dài), theo tính năng, theo ngày × tính năng, kèm tỷ giá và ngày bắt đầu ghi nhận đầy đủ. */
@Service
@RequiredArgsConstructor
public class AiUsageOverviewService {

    static final long MAX_SPAN_DAYS = 366;

    private final AiUsageLogRepository repository;
    private final AiPricing pricing;
    private final SystemConfigService config;
    private final com.lms.common.repository.AiBillingEntryRepository billingRepository;

    public record Totals(BigDecimal costUsd, long tokens, long requests, long audioSeconds) {}

    public record FeatureRow(String feature, BigDecimal costUsd, long tokens, long requests) {}

    public record DayFeature(String day, String feature, BigDecimal costUsd) {}

    /** So sánh với hóa đơn Google AI Studio (chỉ phần Gemini) trên các hóa đơn nằm trọn trong khoảng đang xem: hóa đơn thực tế, phần đã quy được từ token (cùng các ngày), chênh lệch chưa quy được. */
    public record Reconciliation(long billedVnd, long trackedVnd, long unexplainedVnd, int entries) {}

    public record Overview(LocalDate from, LocalDate to, Totals totals, Totals previous, List<FeatureRow> byFeature, List<DayFeature> daily,
                           BigDecimal usdToVnd, String trackingSince, boolean partialCoverage, Reconciliation reconciliation) {}

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to, String feature) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
            throw new InvalidRequestException("Khoảng ngày không hợp lệ: ngày kết thúc phải sau ngày bắt đầu, tối đa " + MAX_SPAN_DAYS + " ngày");
        }
        String feat = feature == null || feature.isBlank() ? null : feature.trim();
        var a = start.atStartOfDay();
        var b = end.plusDays(1).atStartOfDay();
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        var pa = start.minusDays(days).atStartOfDay();

        List<FeatureRow> byFeature = new ArrayList<>();
        for (Object[] r : repository.byFeature(a, b, feat)) {
            byFeature.add(new FeatureRow(String.valueOf(r[0]), dec(r[1]), num(r[2]), num(r[3])));
        }
        List<DayFeature> daily = new ArrayList<>();
        for (Object[] r : repository.dailyByFeature(a, b, feat)) {
            daily.add(new DayFeature(String.valueOf(r[0]), String.valueOf(r[1]), dec(r[2])));
        }
        String since = config.getString("AI_COST_TRACKING_SINCE", "");
        boolean partial = false;
        try {
            partial = !since.isBlank() && start.isBefore(LocalDate.parse(since));
        } catch (RuntimeException ignored) {
            // Ngày cấu hình sai định dạng: bỏ qua cảnh báo phạm vi, không làm vỡ trang.
        }
        return new Overview(start, end, totals(a, b, feat), totals(pa, a, feat), byFeature, daily, pricing.usdToVndRate(), since, partial, reconcile(start, end));
    }

    private Reconciliation reconcile(LocalDate start, LocalDate end) {
        var entries = billingRepository.findWithin(start, end);
        if (entries.isEmpty()) return null;
        BigDecimal rate = pricing.usdToVndRate();
        long billed = 0;
        BigDecimal trackedUsd = BigDecimal.ZERO;
        for (var e : entries) {
            billed += e.getAmountVnd();
            trackedUsd = trackedUsd.add(repository.sumCost(e.getPeriodStart().atStartOfDay(), e.getPeriodEnd().plusDays(1).atStartOfDay()));
        }
        long tracked = trackedUsd.multiply(rate).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return new Reconciliation(billed, tracked, Math.max(0, billed - tracked), entries.size());
    }

    private Totals totals(java.time.LocalDateTime a, java.time.LocalDateTime b, String feature) {
        Object[] r = repository.totals(a, b, feature).get(0);
        return new Totals(dec(r[0]), num(r[1]), num(r[2]), num(r[3]));
    }

    private static BigDecimal dec(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
