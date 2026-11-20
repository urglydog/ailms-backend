package com.lms.report.util;

import com.lms.common.util.VietnamesePdfFonts;
import com.lms.report.service.FinanceReport;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Xuất báo cáo tài chính ra CSV / XLSX / PDF. Báo cáo cho quản trị viên nên dùng tiếng Việt. Giới hạn kích thước: PDF chỉ có tóm tắt + tối đa
 * {@link #PDF_MAX_ROWS} dòng mỗi bảng (server nhỏ, PDF không phải nơi xem toàn bộ dữ liệu); CSV/XLSX có đủ các dòng của báo cáo.
 */
public final class ReportExporter {

    static final int PDF_MAX_ROWS = 60;
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReportExporter() {}

    /** Các dòng "chỉ tiêu - giá trị" của phần tóm tắt, dùng chung cho cả 3 định dạng và email. */
    public static List<String[]> summaryRows(FinanceReport r) {
        FinanceReport.Summary s = r.summary();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Kỳ báo cáo", r.from().format(D) + " - " + r.to().format(D)});
        rows.add(new String[] {"Số đơn thanh toán", String.valueOf(s.orders())});
        rows.add(new String[] {"Doanh thu gộp (đ)", String.valueOf(s.grossRevenue())});
        rows.add(new String[] {"Số khoản hoàn tiền", String.valueOf(s.refundCount())});
        rows.add(new String[] {"Tiền hoàn (đ)", String.valueOf(s.refunds())});
        rows.add(new String[] {"Tỷ lệ hoàn (%)", String.format(Locale.ROOT, "%.1f", s.refundRatePct())});
        rows.add(new String[] {"Doanh thu ròng (đ)", String.valueOf(s.netRevenue())});
        rows.add(new String[] {"Phần nền tảng (đ)", String.valueOf(s.platformFee())});
        rows.add(new String[] {"Phần giảng viên (đ)", String.valueOf(s.instructorEarning())});
        rows.add(new String[] {"Số lần chi trả giảng viên", String.valueOf(s.payoutsPaidCount())});
        rows.add(new String[] {"Đã chi trả giảng viên (đ)", String.valueOf(s.payoutsPaid())});
        rows.add(new String[] {"Số lệnh chi PayOS thành công", String.valueOf(s.payosOrders())});
        rows.add(new String[] {"Chi phí chuyển tiền PayOS do nền tảng chịu (đ)", String.valueOf(s.payosFee())});
        rows.add(new String[] {"Yêu cầu rút đang chờ chi, tại thời điểm xem (đ)", String.valueOf(s.pendingPayouts())});
        rows.add(new String[] {"Nghĩa vụ chưa trả giảng viên, tại thời điểm xem (đ)", String.valueOf(s.obligations())});
        rows.add(new String[] {"Công nợ giảng viên phải thu, tại thời điểm xem (đ)", String.valueOf(s.receivables())});
        FinanceReport.Reconciliation c = r.reconciliation();
        rows.add(new String[] {"Đối soát PayOS: tổng chi qua PayOS (đ)", String.valueOf(c.payosOutflow())});
        rows.add(new String[] {"Đối soát PayOS: đã ghi nhận hoàn tiền (đ)", String.valueOf(c.recordedRefunds())});
        rows.add(new String[] {"Đối soát PayOS: đã ghi nhận chi trả giảng viên (đ)", String.valueOf(c.recordedPayouts())});
        rows.add(new String[] {"Đối soát PayOS: chênh lệch (đ)", String.valueOf(c.difference())});
        return rows;
    }

    private static List<String[]> bucketRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Kỳ", "Số đơn", "Doanh thu gộp (đ)", "Tiền hoàn (đ)", "Phần nền tảng (đ)", "Phần giảng viên (đ)", "Đã chi trả (đ)"});
        r.buckets().forEach(b -> rows.add(new String[] {b.period(), String.valueOf(b.orders()), String.valueOf(b.grossRevenue()), String.valueOf(b.refunds()),
                String.valueOf(b.platformFee()), String.valueOf(b.instructorEarning()), String.valueOf(b.payoutsPaid())}));
        return rows;
    }

    private static List<String[]> reasonRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Lý do hoàn tiền", "Số khoản", "Tiền hoàn (đ)"});
        r.refundReasons().forEach(x -> rows.add(new String[] {x.reason(), String.valueOf(x.count()), String.valueOf(x.amount())}));
        return rows;
    }

    private static List<String[]> sourceRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Nguồn doanh thu", "Số đơn", "Doanh thu gộp (đ)"});
        r.sources().forEach(x -> rows.add(new String[] {x.source().equals("INSTRUCTOR_REFERRAL") ? "Qua liên kết giới thiệu của giảng viên" : "Học viên tự tìm thấy",
                String.valueOf(x.orders()), String.valueOf(x.grossRevenue())}));
        return rows;
    }

    private static List<String[]> mismatchRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Loại khoản", "Mã", "Số tiền (đ)", "Thời điểm", "Nội dung"});
        r.mismatches().forEach(x -> rows.add(new String[] {x.kind().equals("DISPUTE_REFUND") ? "Hoàn tiền" : "Chi trả giảng viên", x.reference(),
                String.valueOf(x.amount()), x.at() == null ? "" : x.at().toString(), x.detail()}));
        return rows;
    }

    private static List<String[]> courseRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Khóa học", "Giảng viên", "Số đơn", "Doanh thu gộp (đ)", "Phần nền tảng (đ)", "Phần giảng viên (đ)", "Đơn đã hoàn"});
        r.courses().forEach(c -> rows.add(new String[] {c.title(), c.instructorName(), String.valueOf(c.orders()), String.valueOf(c.grossRevenue()),
                String.valueOf(c.platformFee()), String.valueOf(c.instructorEarning()), String.valueOf(c.refundedOrders())}));
        return rows;
    }

    private static List<String[]> instructorRows(FinanceReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Giảng viên", "Email", "Số đơn", "Doanh thu gộp (đ)", "Phần giảng viên (đ)", "Đã chi trả (đ)"});
        r.instructors().forEach(i -> rows.add(new String[] {i.name(), i.email(), String.valueOf(i.orders()), String.valueOf(i.grossRevenue()),
                String.valueOf(i.instructorEarning()), String.valueOf(i.payoutsPaid())}));
        return rows;
    }

    /** CSV UTF-8 có BOM (Excel đọc đúng tiếng Việt); các phần cách nhau một dòng trống. Ô bắt đầu bằng = + - @ được đặt dấu nháy đơn phía trước. */
    public static byte[] csv(FinanceReport r) {
        StringBuilder sb = new StringBuilder("﻿");
        appendCsv(sb, "TÓM TẮT", summaryRows(r));
        appendCsv(sb, "THEO KỲ", bucketRows(r));
        appendCsv(sb, "THEO KHÓA HỌC", courseRows(r));
        appendCsv(sb, "THEO GIẢNG VIÊN", instructorRows(r));
        appendCsv(sb, "THEO LÝ DO HOÀN TIỀN", reasonRows(r));
        appendCsv(sb, "THEO NGUỒN DOANH THU", sourceRows(r));
        appendCsv(sb, "KHOẢN CHI PAYOS KHÔNG KHỚP SỔ SÁCH", mismatchRows(r));
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void appendCsv(StringBuilder sb, String title, List<String[]> rows) {
        sb.append(cell(title)).append('\n');
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                sb.append(i == 0 ? "" : ",").append(cell(row[i]));
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    static String cell(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** XLSX 4 sheet (Tóm tắt, Theo kỳ, Theo khóa học, Theo giảng viên), ghi dạng luồng. Số tiền ghi dạng số để Excel cộng được. */
    public static byte[] xlsx(FinanceReport r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (SimpleXlsxWriter w = new SimpleXlsxWriter(out)) {
                w.sheet("Tóm tắt", numeric(summaryRows(r), 1, true));
                w.sheet("Theo kỳ", numeric(bucketRows(r), 1, false));
                w.sheet("Theo khóa học", numeric(courseRows(r), 2, false));
                w.sheet("Theo giảng viên", numeric(instructorRows(r), 2, false));
                w.sheet("Lý do hoàn tiền", numeric(reasonRows(r), 1, false));
                w.sheet("Nguồn doanh thu", numeric(sourceRows(r), 1, false));
                w.sheet("Chi PayOS không khớp", numeric(mismatchRows(r), 2, false));
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được file XLSX", e);
        }
    }

    /** Chuyển các cột từ {@code firstNumeric} trở đi (hoặc cột giá trị của sheet tóm tắt) thành số nếu parse được. */
    private static List<Object[]> numeric(List<String[]> rows, int firstNumeric, boolean summary) {
        List<Object[]> out = new ArrayList<>();
        boolean header = true;
        for (String[] row : rows) {
            Object[] o = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                o[i] = (!header || summary) && i >= firstNumeric && isNumber(row[i]) ? number(row[i]) : row[i];
            }
            out.add(o);
            header = false;
        }
        return out;
    }

    /** Số nguyên ghi dạng Long (không có ".0"), số lẻ (vd tỷ lệ %) ghi dạng Double. */
    private static Object number(String raw) {
        double d = Double.parseDouble(raw);
        return d == Math.rint(d) && Math.abs(d) < 9e15 ? (Object) Long.valueOf((long) d) : (Object) Double.valueOf(d);
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static byte[] pdf(FinanceReport r) {
        try {
            BaseFont regular = VietnamesePdfFonts.loadRegular();
            BaseFont bold = VietnamesePdfFonts.loadBold();
            Font body = new Font(regular, 9);
            Font strong = new Font(bold, 9);
            Font title = new Font(bold, 16);
            Font heading = new Font(bold, 11);
            Font small = new Font(regular, 8, Font.NORMAL, new Color(0x55, 0x5E, 0x6B));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Báo cáo tài chính LinguaLearn", title));
            doc.add(new Paragraph(r.from().format(D) + " - " + r.to().format(D), small));
            doc.add(section("Tóm tắt", heading));
            doc.add(table(summaryRows(r), new float[] {4f, 1.5f}, false, body, strong, 0));
            addTable(doc, "Theo kỳ", bucketRows(r), new float[] {1.3f, 1f, 1.6f, 1.4f, 1.6f, 1.6f, 1.4f}, heading, body, strong, 1);
            addTable(doc, "Theo khóa học (nhiều doanh thu nhất)", courseRows(r), new float[] {4f, 2.4f, 0.9f, 1.6f, 1.5f, 1.6f, 1f}, heading, body, strong, 2);
            addTable(doc, "Theo giảng viên (nhiều doanh thu nhất)", instructorRows(r), new float[] {2.4f, 3f, 0.9f, 1.6f, 1.6f, 1.5f}, heading, body, strong, 2);
            addTable(doc, "Theo lý do hoàn tiền", reasonRows(r), new float[] {3f, 1f, 1.6f}, heading, body, strong, 1);
            addTable(doc, "Theo nguồn doanh thu", sourceRows(r), new float[] {3f, 1f, 1.6f}, heading, body, strong, 1);
            if (r.mismatches().size() > 0) {
                addTable(doc, "Khoản chi PayOS không khớp sổ sách", mismatchRows(r), new float[] {1.5f, 1.5f, 1.2f, 1.8f, 4f}, heading, body, strong, 2);
            }
            boolean cut = r.buckets().size() > PDF_MAX_ROWS || r.courses().size() > PDF_MAX_ROWS || r.instructors().size() > PDF_MAX_ROWS;
            if (cut) {
                Paragraph note = new Paragraph("Mỗi bảng hiển thị " + PDF_MAX_ROWS + " dòng đầu. Xem đầy đủ trong file Excel hoặc CSV của cùng báo cáo.", small);
                note.setSpacingBefore(10);
                doc.add(note);
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Không tạo được PDF báo cáo", e);
        }
    }

    private static Paragraph section(String text, Font f) {
        Paragraph p = new Paragraph(text, f);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private static void addTable(Document doc, String title, List<String[]> rows, float[] widths, Font heading, Font body, Font strong, int firstRight)
            throws DocumentException {
        doc.add(section(title, heading));
        List<String[]> limited = rows.size() > PDF_MAX_ROWS + 1 ? rows.subList(0, PDF_MAX_ROWS + 1) : rows;
        doc.add(table(limited, widths, true, body, strong, firstRight));
    }

    private static PdfPTable table(List<String[]> rows, float[] widths, boolean header, Font body, Font strong, int firstRight) throws DocumentException {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                boolean head = header && r == 0;
                String text = !head && c >= firstRight && isNumber(row[c]) && firstRight > 0 || (!header && c == 1 && isNumber(row[c]))
                        ? money(row[c]) : row[c];
                PdfPCell cell = new PdfPCell(new Phrase(text, head ? strong : body));
                cell.setPadding(3);
                cell.setBorderColor(new Color(0xD0, 0xD5, 0xDD));
                if (c >= firstRight && (firstRight > 0 || !header && c == 1)) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(cell);
            }
        }
        return t;
    }

    private static String money(String raw) {
        try {
            double d = Double.parseDouble(raw);
            return d == Math.rint(d) ? String.format(Locale.ROOT, "%,d", (long) d).replace(',', '.') : raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
