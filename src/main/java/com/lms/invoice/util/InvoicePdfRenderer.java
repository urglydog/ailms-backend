package com.lms.invoice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.common.util.VietnamesePdfFonts;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceStatus;
import com.lms.invoice.entity.InvoiceType;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sinh PDF chứng từ từ dữ liệu ĐÃ CHỤP (entity + dòng + snapshot) — không đọc lại giá/tên hiện tại, nên chứng từ cũ không đổi.
 * Tiếng Anh mặc định (quyết định 15/11/2026); font DejaVu nhúng để tên tiếng Việt có dấu in đúng. Không lưu file, sinh theo yêu cầu.
 */
public final class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color LINE = new Color(0xD0, 0xD5, 0xDD);
    private static final Color MUTED = new Color(0x55, 0x5E, 0x6B);

    private InvoicePdfRenderer() {}

    public static byte[] render(Invoice invoice, List<InvoiceLine> lines, JsonNode snap) {
        try {
            BaseFont regular = VietnamesePdfFonts.loadRegular();
            BaseFont bold = VietnamesePdfFonts.loadBold();
            Font body = new Font(regular, 10);
            Font small = new Font(regular, 8, Font.NORMAL, MUTED);
            Font strong = new Font(bold, 10);
            Font title = new Font(bold, 18);
            Font heading = new Font(bold, 11);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph(text(snap, "issuerName", "LinguaLearn"), heading));
            String note = text(snap, "issuerNote", "");
            if (!note.isBlank()) {
                doc.add(new Paragraph(note, small));
            }
            Paragraph titleP = new Paragraph(titleOf(invoice.getType()), title);
            titleP.setSpacingBefore(14);
            doc.add(titleP);
            if (invoice.getStatus() == InvoiceStatus.VOIDED) {
                doc.add(new Paragraph("VOIDED - this document has been cancelled", new Font(bold, 10, Font.NORMAL, new Color(0xB4, 0x23, 0x18))));
            }

            PdfPTable meta = new PdfPTable(new float[] {1f, 2.2f});
            meta.setWidthPercentage(100);
            meta.setSpacingBefore(10);
            metaRow(meta, "Number", invoice.getNumber(), small, strong);
            metaRow(meta, "Date", invoice.getIssuedAt().format(DATE_TIME), small, body);
            metaRow(meta, partyLabel(invoice.getType()), text(snap, "partyName", ""), small, body);
            metaRow(meta, "Email", text(snap, "partyEmail", ""), small, body);
            extraMeta(meta, invoice.getType(), snap, small, body);
            doc.add(meta);

            PdfPTable table = new PdfPTable(new float[] {5f, 1f, 2f, 2f});
            table.setWidthPercentage(100);
            table.setSpacingBefore(16);
            header(table, "Description", Element.ALIGN_LEFT, strong);
            header(table, "Qty", Element.ALIGN_RIGHT, strong);
            header(table, "Unit price", Element.ALIGN_RIGHT, strong);
            header(table, "Amount", Element.ALIGN_RIGHT, strong);
            for (InvoiceLine l : lines) {
                cell(table, l.getDescription(), Element.ALIGN_LEFT, body);
                cell(table, String.valueOf(l.getQuantity()), Element.ALIGN_RIGHT, body);
                cell(table, money(l.getUnitPrice()), Element.ALIGN_RIGHT, body);
                cell(table, money(l.getAmount()), Element.ALIGN_RIGHT, body);
            }
            doc.add(table);

            PdfPTable totals = new PdfPTable(new float[] {5f, 2f});
            totals.setWidthPercentage(60);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(10);
            if (invoice.getDiscount().signum() > 0) {
                totalRow(totals, "Subtotal", money(invoice.getSubtotal()), body);
                totalRow(totals, "Discount", "-" + money(invoice.getDiscount()), body);
            }
            totalRow(totals, totalLabel(invoice.getType()), money(invoice.getTotal()), strong);
            doc.add(totals);

            String feeNote = text(snap, "feeNote", "");
            if (!feeNote.isBlank()) {
                Paragraph p = new Paragraph(feeNote, small);
                p.setSpacingBefore(12);
                doc.add(p);
            }
            Paragraph footer = new Paragraph("Internal electronic receipt, not a VAT invoice. Generated from data recorded on the issue date; "
                    + "amounts in " + invoice.getCurrency() + ".", small);
            footer.setSpacingBefore(24);
            doc.add(footer);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Không tạo được PDF chứng từ " + invoice.getNumber(), e);
        }
    }

    private static void extraMeta(PdfPTable meta, InvoiceType type, JsonNode snap, Font label, Font value) {
        switch (type) {
            case RECEIPT -> {
                optionalMeta(meta, "Billing name", text(snap, "billingName", ""), label, value);
                optionalMeta(meta, "Phone", text(snap, "billingPhone", ""), label, value);
                optionalMeta(meta, "Payment method", text(snap, "paymentMethod", ""), label, value);
                optionalMeta(meta, "Gateway transaction", text(snap, "gatewayTxnNo", ""), label, value);
                if (snap.has("orders") && snap.get("orders").isArray()) {
                    StringBuilder sb = new StringBuilder();
                    snap.get("orders").forEach(n -> sb.append(sb.length() == 0 ? "" : ", ").append(n.asText()));
                    optionalMeta(meta, "Order reference", sb.toString(), label, value);
                }
            }
            case REFUND -> {
                optionalMeta(meta, "Order reference", text(snap, "orderRef", ""), label, value);
                optionalMeta(meta, "Original receipt", text(snap, "originalReceipt", ""), label, value);
                optionalMeta(meta, "Bank transfer reference", text(snap, "transferRef", ""), label, value);
                optionalMeta(meta, "Transferred at", formatTime(text(snap, "transferredAt", "")), label, value);
            }
            case PAYOUT -> {
                optionalMeta(meta, "Payout request", text(snap, "payoutCode", ""), label, value);
                optionalMeta(meta, "Bank", text(snap, "bankName", ""), label, value);
                optionalMeta(meta, "Account", text(snap, "accountMasked", ""), label, value);
                optionalMeta(meta, "Account holder", text(snap, "holderName", ""), label, value);
                optionalMeta(meta, "Bank transfer reference", text(snap, "transferRef", ""), label, value);
            }
            case DEBT_RECEIPT -> optionalMeta(meta, "Payment code", text(snap, "orderCode", ""), label, value);
            case STATEMENT -> {
                optionalMeta(meta, "Period", text(snap, "periodLabel", text(snap, "period", "")), label, value);
                optionalMeta(meta, "Earnings", amount(snap, "earnings"), label, value);
                optionalMeta(meta, "Refund reversals", amount(snap, "refundReversals"), label, value);
                optionalMeta(meta, "Adjustments", amount(snap, "adjustments"), label, value);
                optionalMeta(meta, "Debt repayments", amount(snap, "debtRepayments"), label, value);
                optionalMeta(meta, "Payouts paid", amount(snap, "payoutsPaid"), label, value);
                optionalMeta(meta, "Platform share on these orders", amount(snap, "platformShare"), label, value);
            }
        }
    }

    private static String titleOf(InvoiceType type) {
        return switch (type) {
            case RECEIPT -> "Payment Receipt";
            case REFUND -> "Refund Voucher";
            case PAYOUT -> "Payout Voucher";
            case DEBT_RECEIPT -> "Debt Repayment Receipt";
            case STATEMENT -> "Monthly Earnings Statement";
        };
    }

    private static String partyLabel(InvoiceType type) {
        return type == InvoiceType.RECEIPT || type == InvoiceType.REFUND ? "Customer" : "Instructor";
    }

    private static String totalLabel(InvoiceType type) {
        return switch (type) {
            case RECEIPT -> "Total paid";
            case REFUND -> "Total refunded";
            case PAYOUT -> "Total paid out";
            case DEBT_RECEIPT -> "Total repaid";
            case STATEMENT -> "Net movement";
        };
    }

    private static String text(JsonNode snap, String field, String fallback) {
        JsonNode n = snap == null ? null : snap.get(field);
        return n == null || n.isNull() ? fallback : n.asText();
    }

    private static String amount(JsonNode snap, String field) {
        JsonNode n = snap == null ? null : snap.get(field);
        return n == null || n.isNull() ? "" : money(BigDecimal.valueOf(n.asLong()));
    }

    private static String formatTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    /** 1234567 -> "1.234.567 VND" (kiểu Việt Nam, không có phần lẻ vì VND không dùng đơn vị nhỏ hơn). */
    public static String money(BigDecimal v) {
        return String.format(java.util.Locale.ROOT, "%,d", v.setScale(0, java.math.RoundingMode.HALF_UP).longValue()).replace(',', '.') + " VND";
    }

    private static void optionalMeta(PdfPTable t, String label, String value, Font lf, Font vf) {
        if (value != null && !value.isBlank()) {
            metaRow(t, label, value, lf, vf);
        }
    }

    private static void metaRow(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell a = new PdfPCell(new Phrase(label, lf));
        a.setBorder(PdfPCell.NO_BORDER);
        a.setPaddingBottom(3);
        PdfPCell b = new PdfPCell(new Phrase(value, vf));
        b.setBorder(PdfPCell.NO_BORDER);
        b.setPaddingBottom(3);
        t.addCell(a);
        t.addCell(b);
    }

    private static void header(PdfPTable t, String s, int align, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(align);
        c.setBorder(PdfPCell.BOTTOM);
        c.setBorderColor(LINE);
        c.setPaddingBottom(6);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String s, int align, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(align);
        c.setBorder(PdfPCell.BOTTOM);
        c.setBorderColor(LINE);
        c.setPadding(6);
        t.addCell(c);
    }

    private static void totalRow(PdfPTable t, String label, String value, Font f) {
        PdfPCell a = new PdfPCell(new Phrase(label, f));
        a.setBorder(PdfPCell.NO_BORDER);
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell b = new PdfPCell(new Phrase(value, f));
        b.setBorder(PdfPCell.NO_BORDER);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }
}
