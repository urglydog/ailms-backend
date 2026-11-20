package com.lms.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.Role;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.SystemConfigService;
import com.lms.report.entity.ReportSnapshot;
import com.lms.report.repository.ReportSnapshotRepository;
import com.lms.report.service.FinanceReport.Granularity;
import com.lms.report.util.ReportExporter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Thống kê tài chính admin + báo cáo tự động (20/11/2026, Hóa đơn H3–H4). {@link #build} chỉ ĐỌC (không đổi dữ liệu); {@link #generate} lưu bản chụp
 * {@code report_snapshots} rồi gửi email kèm XLSX + PDF. Báo cáo định kỳ chống trùng bằng {@code dedupe_key} UNIQUE nên chạy lại/bù không gửi 2 lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    static final long MAX_SPAN_DAYS = 800;
    static final int TOP_ROWS = 200;
    static final int RUN_HOUR = 6;

    private final FinanceReportQueries queries;
    private final ReportSnapshotRepository snapshotRepository;
    private final SystemConfigService config;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public FinanceReport build(LocalDate from, LocalDate to, Granularity granularity) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new InvalidRequestException("Khoảng thời gian không hợp lệ: ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
            throw new InvalidRequestException("Khoảng thời gian tối đa là " + MAX_SPAN_DAYS + " ngày");
        }
        LocalDateTime a = from.atStartOfDay();
        LocalDateTime b = to.plusDays(1).atStartOfDay();
        Granularity g = granularity == null ? Granularity.DAY : granularity;
        // Kỳ liền trước có cùng độ dài, để so sánh tăng/giảm.
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDateTime pa = from.minusDays(days).atStartOfDay();
        return new FinanceReport(from, to, g, queries.summary(a, b), queries.summary(pa, a), queries.buckets(a, b, g), queries.courses(a, b, TOP_ROWS),
                queries.instructors(a, b, TOP_ROWS), queries.reconciliation(a, b), queries.refundReasons(a, b), queries.sources(a, b),
                queries.payoutPipeline(), queries.mismatches(a, b));
    }

    public record Generated(ReportSnapshot snapshot, FinanceReport report, int emailsSent) {}

    /** Tạo báo cáo, lưu bản chụp và gửi email. Trả {@code null} nếu báo cáo định kỳ (có dedupeKey) đã được tạo trước đó. */
    public Generated generate(String type, LocalDate from, LocalDate to, String trigger, Long userId, String dedupeKey, List<String> recipients) {
        if (dedupeKey != null && snapshotRepository.existsByDedupeKey(dedupeKey)) {
            return null;
        }
        FinanceReport report = build(from, to, type.equals("DAILY") ? Granularity.DAY : type.equals("MONTHLY") ? Granularity.WEEK : Granularity.DAY);
        ReportSnapshot snap = new ReportSnapshot();
        snap.setReportType(type);
        snap.setPeriodFrom(from);
        snap.setPeriodTo(to);
        snap.setTriggerType(trigger);
        snap.setDedupeKey(dedupeKey);
        snap.setCreatedByUserId(userId);
        snap.setSummaryJson(toJson(report.summary()));
        try {
            snap = snapshotRepository.save(snap);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
        int sent = 0;
        List<String> sentTo = new ArrayList<>();
        if (recipients != null && !recipients.isEmpty()) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            String base = "bao-cao-tai-chinh-" + from + "_" + to;
            files.put(base + ".xlsx", ReportExporter.xlsx(report));
            files.put(base + ".pdf", ReportExporter.pdf(report));
            String subject = "Báo cáo tài chính " + label(type) + " " + from + (from.equals(to) ? "" : " - " + to) + " - LinguaLearn";
            String intro = "Báo cáo tài chính " + label(type) + " của LinguaLearn cho kỳ " + from + (from.equals(to) ? "" : " đến " + to)
                    + " đã sẵn sàng.";
            for (String r : recipients) {
                if (emailService.sendReportEmail(r, subject, "Báo cáo tài chính " + label(type), intro, ReportExporter.summaryRows(report), files)) {
                    sent++;
                    sentTo.add(r);
                }
            }
            snap.setEmailedTo(String.join(", ", sentTo).substring(0, Math.min(500, String.join(", ", sentTo).length())));
            snap = snapshotRepository.save(snap);
        }
        return new Generated(snap, report, sent);
    }

    /** Người nhận: cấu hình {@code REPORT_RECIPIENTS} (cách nhau dấu phẩy) hoặc tất cả quản trị viên đang hoạt động nếu để trống. */
    public List<String> recipients() {
        String raw = config.getString("REPORT_RECIPIENTS", "");
        List<String> list = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String e = part.trim();
                if (deliverable(e) && !list.contains(e)) {
                    list.add(e);
                }
            }
            return list;
        }
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            if (!Boolean.FALSE.equals(admin.getIsActive()) && admin.getEmail() != null && deliverable(admin.getEmail()) && !list.contains(admin.getEmail())) {
                list.add(admin.getEmail());
            }
        }
        return list;
    }

    /**
     * Phát các báo cáo định kỳ đến hạn (idempotent nhờ dedupeKey, nên gọi nhiều lần hoặc bù sau khi server tắt đều an toàn): sau {@link #RUN_HOUR} giờ sáng,
     * báo cáo ngày hôm qua / tuần Thứ Hai–Chủ nhật vừa xong / tháng vừa xong nếu công tắc tương ứng đang bật. Trả số báo cáo đã tạo.
     */
    public int runScheduled(LocalDateTime now) {
        if (now.getHour() < RUN_HOUR) {
            return 0;
        }
        LocalDate today = now.toLocalDate();
        int made = 0;
        List<String> to = recipients();
        if (config.getInt("REPORT_DAILY_ENABLED", 0) == 1) {
            LocalDate d = today.minusDays(1);
            made += count(generate("DAILY", d, d, "SCHEDULED", null, "DAILY:" + d, to));
        }
        if (config.getInt("REPORT_WEEKLY_ENABLED", 1) == 1) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            made += count(generate("WEEKLY", monday, monday.plusDays(6), "SCHEDULED", null, "WEEKLY:" + monday, to));
        }
        if (config.getInt("REPORT_MONTHLY_ENABLED", 1) == 1) {
            LocalDate first = today.withDayOfMonth(1).minusMonths(1);
            made += count(generate("MONTHLY", first, first.with(TemporalAdjusters.lastDayOfMonth()), "SCHEDULED", null, "MONTHLY:" + first, to));
        }
        return made;
    }

    /** Địa chỉ gửi được thật: có {@code @} và không thuộc miền nội bộ {@code .local} (không có người nhận, thư sẽ bị trả lại). */
    static boolean deliverable(String email) {
        String e = email.trim().toLowerCase(java.util.Locale.ROOT);
        return e.contains("@") && !e.endsWith(".local");
    }

    public List<ReportSnapshot> recent(int limit) {
        return snapshotRepository.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    private static int count(Generated g) {
        return g == null ? 0 : 1;
    }

    private static String label(String type) {
        return switch (type) {
            case "DAILY" -> "hằng ngày";
            case "WEEKLY" -> "hằng tuần";
            case "MONTHLY" -> "hằng tháng";
            default -> "theo yêu cầu";
        };
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được bản chụp báo cáo", e);
        }
    }
}
