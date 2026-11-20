package com.lms.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.report.service.FinanceReport;
import com.lms.report.service.FinanceReport.Bucket;
import com.lms.report.service.FinanceReport.CourseRow;
import com.lms.report.service.FinanceReport.Granularity;
import com.lms.report.service.FinanceReport.InstructorRow;
import com.lms.report.service.FinanceReport.Reconciliation;
import com.lms.report.service.FinanceReport.Summary;
import com.lms.report.util.ReportExporter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportExporterTest {

    private static FinanceReport sample(String courseTitle) {
        Summary s = new Summary(3, 300_000, 1, 100_000, 33.3, 200_000, 60_000, 40_000, 1, 20_000, 2, 5_900, 0, 40_000, 0);
        return new FinanceReport(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), Granularity.DAY, s, s,
                List.of(new Bucket("2026-11-18", 3, 300_000, 100_000, 60_000, 40_000, 20_000)),
                List.of(new CourseRow(1, courseTitle, "Giảng Viên Ưu", 3, 300_000, 60_000, 40_000, 1)),
                List.of(new InstructorRow(2, "Giảng Viên Ưu", "gv@gmail.com", 3, 300_000, 40_000, 20_000)),
                new Reconciliation(30_000, 10_000, 20_000, 0, 2, 2),
                List.of(new FinanceReport.ReasonRow("CHANGED_MIND", 1, 100_000)), List.of(new FinanceReport.SourceRow("ORGANIC", 3, 300_000)),
                List.of(new FinanceReport.PayoutStage("REQUESTED", 1, 20_000, 2)),
                List.of(new FinanceReport.Mismatch("DISPUTE_REFUND", "LLR9-1", 10_000, java.time.LocalDateTime.of(2026, 11, 18, 9, 0), "PayOS đã chi nhưng hệ thống chưa ghi nhận")));
    }

    @Test
    void csv_hasBomSectionsAndNeutralisesFormulas() {
        String csv = new String(ReportExporter.csv(sample("=cmd|' /C calc'!A0")), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿").contains("TÓM TẮT").contains("THEO KỲ").contains("THEO KHÓA HỌC").contains("THEO GIẢNG VIÊN");
        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"").doesNotContain(",\"=cmd");
        assertThat(csv).contains("\"Doanh thu gộp (đ)\",\"300000\"").contains("THEO LÝ DO HOÀN TIỀN").contains("LLR9-1");
    }

    @Test
    void xlsxAndPdf_produceRealFilesWithVietnameseText() {
        byte[] xlsx = ReportExporter.xlsx(sample("Khóa học Tiếng Nhật"));
        byte[] pdf = ReportExporter.pdf(sample("Khóa học Tiếng Nhật"));

        assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(3000);
        // Không có chữ "demo" trong bất kỳ nội dung nào người dùng đọc.
        assertThat(new String(ReportExporter.csv(sample("X")), StandardCharsets.UTF_8).toLowerCase()).doesNotContain("demo");
    }

    @Test
    void summaryRows_includeReconciliationAndPayosFeeLines() {
        List<String[]> rows = ReportExporter.summaryRows(sample("X"));

        assertThat(rows).extracting(r -> r[0]).anyMatch(l -> l.contains("PayOS do nền tảng chịu")).anyMatch(l -> l.contains("chênh lệch"));
    }
}
