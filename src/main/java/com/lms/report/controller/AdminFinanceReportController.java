package com.lms.report.controller;

import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.InvalidRequestException;
import com.lms.report.entity.ReportSnapshot;
import com.lms.report.service.FinanceReport;
import com.lms.report.service.FinanceReport.Granularity;
import com.lms.report.service.FinanceReportService;
import com.lms.report.util.ReportExporter;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thống kê tài chính toàn nền tảng, xuất báo cáo và báo cáo tự động (chỉ ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/finance-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceReportController {

    private final FinanceReportService reportService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<FinanceReport> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") Granularity granularity) {
        return ResponseEntity.ok(reportService.build(from, to, granularity));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") Granularity granularity,
            @RequestParam String format) {
        FinanceReport r = reportService.build(from, to, granularity);
        String base = "bao-cao-tai-chinh-" + from + "_" + to;
        return switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "csv" -> file(ReportExporter.csv(r), base + ".csv", new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
            case "xlsx" -> file(ReportExporter.xlsx(r), base + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            case "pdf" -> file(ReportExporter.pdf(r), base + ".pdf", MediaType.APPLICATION_PDF);
            default -> throw new InvalidRequestException("Định dạng không hỗ trợ: chỉ csv, xlsx hoặc pdf");
        };
    }

    /** Lịch sử các báo cáo đã tạo (định kỳ và chạy tay), mới nhất trước. */
    @GetMapping("/snapshots")
    public ResponseEntity<List<SnapshotRes>> snapshots(@RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(reportService.recent(limit).stream().map(SnapshotRes::of).toList());
    }

    /** "Chạy ngay": tạo báo cáo cho kỳ chọn và gửi email cho CHÍNH admin bấm (không gửi cho cả danh sách người nhận định kỳ). */
    @PostMapping("/run")
    public ResponseEntity<RunRes> run(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var admin = userRepository.findByEmail(principal.getName()).orElseThrow(() -> new InvalidRequestException("Không xác định được quản trị viên"));
        var g = reportService.generate("MANUAL", from, to, "MANUAL", admin.getId(), null, List.of(admin.getEmail()));
        return ResponseEntity.ok(new RunRes(SnapshotRes.of(g.snapshot()), g.emailsSent()));
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, String name, MediaType type) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"").contentType(type).body(bytes);
    }

    public record SnapshotRes(Long id, String reportType, LocalDate periodFrom, LocalDate periodTo, String triggerType, String emailedTo,
                              java.time.LocalDateTime createdAt) {
        static SnapshotRes of(ReportSnapshot s) {
            return new SnapshotRes(s.getId(), s.getReportType(), s.getPeriodFrom(), s.getPeriodTo(), s.getTriggerType(), s.getEmailedTo(), s.getCreatedAt());
        }
    }

    public record RunRes(SnapshotRes snapshot, int emailsSent) {}
}
