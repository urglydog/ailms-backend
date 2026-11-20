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
    private static final Color ACCENT = new Color(0x1A, 0x6F, 0xE0);
    private static final Color INK = new Color(0x0A, 0x25, 0x40);
    private static final Color TINT = new Color(0xF3, 0xF9, 0xFF);
    private static final Color ZEBRA = new Color(0xF8, 0xFA, 0xFC);
    private static final Color DANGER = new Color(0xB4, 0x23, 0x18);

    private InvoicePdfRenderer() {}

    public static byte[] render(Invoice invoice, List<InvoiceLine> lines, JsonNode snap) {
        return render(invoice, lines, snap, null, InvoiceChecksum.display(InvoiceChecksum.sha256(invoice, lines)));
    }

    /**
     * Bố cục (A4): dải đầu (bên phát hành | loại chứng từ + số), khối thông tin hai cột (bên phát hành | người nhận), hàng thông tin chung, bảng dòng căn thẳng hàng,
     * khung tổng tiền, chi tiết giao dịch, rồi chân trang gồm ghi chú, mã kiểm và QR mở thẳng bản PDF gốc ({@code qrUrl} null thì không có QR).
     */
    public static byte[] render(Invoice invoice, List<InvoiceLine> lines, JsonNode snap, String qrUrl, String checksumDisplay) {
        try {
            BaseFont regular = VietnamesePdfFonts.loadRegular();
            BaseFont bold = VietnamesePdfFonts.loadBold();
            Font body = new Font(regular, 9.5f);
            Font small = new Font(regular, 8, Font.NORMAL, MUTED);
            Font strong = new Font(bold, 9.5f);
            Font label = new Font(bold, 7.5f, Font.NORMAL, MUTED);
            Font white = new Font(bold, 9, Font.NORMAL, Color.WHITE);
            Font title = new Font(bold, 19, Font.NORMAL, ACCENT);
            Font brand = new Font(bold, 15, Font.NORMAL, INK);
            Font mono = new Font(bold, 10, Font.NORMAL, INK);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 44);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // 1) Dải đầu
            PdfPTable head = new PdfPTable(new float[] {1.1f, 1f});
            head.setWidthPercentage(100);
            PdfPCell left = plain(new Phrase("", small), Element.ALIGN_LEFT);
            left.setPhrase(null);
            left.addElement(new Paragraph(text(snap, "issuerName", "LinguaLearn"), brand));
            String note = text(snap, "issuerNote", "");
            if (!note.isBlank()) {
                left.addElement(new Paragraph(note, small));
            }
            head.addCell(left);
            PdfPCell right = plain(new Phrase("", small), Element.ALIGN_RIGHT);
            right.setPhrase(null);
            Paragraph titleP = new Paragraph(titleOf(invoice.getType()).toUpperCase(java.util.Locale.ROOT), title);
            titleP.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(titleP);
            Paragraph num = new Paragraph(invoice.getNumber(), mono);
            num.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(num);
            if (invoice.getStatus() == InvoiceStatus.VOIDED) {
                Paragraph v = new Paragraph("VOIDED - this document has been cancelled", new Font(bold, 8.5f, Font.NORMAL, DANGER));
                v.setAlignment(Element.ALIGN_RIGHT);
                right.addElement(v);
            }
            head.addCell(right);
            doc.add(head);
            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            PdfPCell line = new PdfPCell(new Phrase(" ", new Font(regular, 2)));
            line.setBorder(PdfPCell.BOTTOM);
            line.setBorderColor(ACCENT);
            line.setBorderWidthBottom(2f);
            rule.addCell(line);
            rule.setSpacingAfter(12);
            doc.add(rule);

            // 2) Hai khối thông tin: bên phát hành | người nhận
            PdfPTable parties = new PdfPTable(new float[] {1f, 1f});
            parties.setWidthPercentage(100);
            parties.setSpacingAfter(10);
            parties.addCell(block("ISSUED BY", List.of(text(snap, "issuerName", "LinguaLearn"), note), label, strong, body));
            List<String> to = new java.util.ArrayList<>();
            to.add(text(snap, "partyName", ""));
            to.add(text(snap, "partyEmail", ""));
            String billing = text(snap, "billingName", "");
            if (!billing.isBlank() && !billing.equals(text(snap, "partyName", ""))) {
                to.add("Billing name: " + billing);
            }
            String phone = text(snap, "billingPhone", "");
            if (!phone.isBlank()) {
                to.add("Phone: " + phone);
            }
            parties.addCell(block(partyLabel(invoice.getType()).toUpperCase(java.util.Locale.ROOT), to, label, strong, body));
            doc.add(parties);

            // 3) Thông tin chung
            PdfPTable meta = new PdfPTable(new float[] {1f, 1f, 1f, 1f});
            meta.setWidthPercentage(100);
            meta.setSpacingAfter(12);
            metaCell(meta, "DOCUMENT NO.", invoice.getNumber(), label, body);
            metaCell(meta, "DATE", invoice.getIssuedAt().format(DATE_TIME), label, body);
            metaCell(meta, "CURRENCY", invoice.getCurrency(), label, body);
            metaCell(meta, "STATUS", invoice.getStatus() == InvoiceStatus.VOIDED ? "Voided" : "Issued", label, body);
            doc.add(meta);

            // 4) Bảng dòng
            PdfPTable table = new PdfPTable(new float[] {5.4f, 0.8f, 1.9f, 1.9f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            header(table, "Description", Element.ALIGN_LEFT, white);
            header(table, "Qty", Element.ALIGN_RIGHT, white);
            header(table, "Unit price", Element.ALIGN_RIGHT, white);
            header(table, "Amount", Element.ALIGN_RIGHT, white);
            boolean zebra = false;
            for (InvoiceLine l : lines) {
                Color bg = zebra ? ZEBRA : Color.WHITE;
                cell(table, l.getDescription(), Element.ALIGN_LEFT, body, bg);
                cell(table, String.valueOf(l.getQuantity()), Element.ALIGN_RIGHT, body, bg);
                cell(table, money(l.getUnitPrice()), Element.ALIGN_RIGHT, body, bg);
                cell(table, money(l.getAmount()), Element.ALIGN_RIGHT, body, bg);
                zebra = !zebra;
            }
            doc.add(table);

            // 5) Khung tổng tiền (căn phải)
            PdfPTable totals = new PdfPTable(new float[] {2.2f, 1.8f});
            totals.setWidthPercentage(52);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(8);
            if (invoice.getDiscount().signum() > 0) {
                totalRow(totals, "Subtotal", money(invoice.getSubtotal()), body, null);
                totalRow(totals, "Discount", "-" + money(invoice.getDiscount()), body, null);
            }
            totalRow(totals, totalLabel(invoice.getType()), money(invoice.getTotal()), new Font(bold, 11, Font.NORMAL, ACCENT), TINT);
            doc.add(totals);

            // 6) Chi tiết giao dịch (chỉ hiện khi có dòng nào)
            PdfPTable details = new PdfPTable(new float[] {1f, 2.4f});
            details.setWidthPercentage(100);
            extraMeta(details, invoice.getType(), snap, label, body);
            if (details.size() > 0) {
                Paragraph h = new Paragraph("TRANSACTION DETAILS", label);
                h.setSpacingBefore(14);
                h.setSpacingAfter(3);
                doc.add(h);
                doc.add(details);
            }

            // 7) Chân trang: ghi chú | QR + mã kiểm
            String feeNote = text(snap, "feeNote", "");
            PdfPTable foot = new PdfPTable(new float[] {3.2f, 1.1f});
            foot.setWidthPercentage(100);
            foot.setSpacingBefore(18);
            PdfPCell notes = plain(new Phrase("", small), Element.ALIGN_LEFT);
            notes.setPhrase(null);
            if (!feeNote.isBlank()) {
                notes.addElement(new Paragraph(feeNote, small));
            }
            Paragraph legal = new Paragraph("Internal electronic receipt, not a VAT invoice. Generated from data recorded on the issue date; amounts in "
                    + invoice.getCurrency() + ".", small);
            legal.setSpacingBefore(4);
            notes.addElement(legal);
            Paragraph chk = new Paragraph("Checksum (SHA-256): " + checksumDisplay, new Font(bold, 8.5f, Font.NORMAL, INK));
            chk.setSpacingBefore(6);
            notes.addElement(chk);
            Paragraph chkNote = new Paragraph("Identical content always produces the same checksum; compare it with the one shown when you open the original via the QR code.", small);
            notes.addElement(chkNote);
            foot.addCell(notes);
            PdfPCell qrCell = plain(new Phrase("", small), Element.ALIGN_CENTER);
            qrCell.setPhrase(null);
            if (qrUrl != null && !qrUrl.isBlank()) {
                com.lowagie.text.Image qr = com.lowagie.text.Image.getInstance(com.lms.certificate.util.CertificateQrCode.generate(qrUrl, 220), null);
                qr.scaleAbsolute(78, 78);
                qr.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
                qrCell.addElement(qr);
                Paragraph cap = new Paragraph("Scan to open the original document", small);
                cap.setAlignment(Element.ALIGN_CENTER);
                qrCell.addElement(cap);
            }
            foot.addCell(qrCell);
            doc.add(foot);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Không tạo được PDF chứng từ " + invoice.getNumber(), e);
        }
    }

    private static PdfPCell plain(Phrase phrase, int align) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBorder(PdfPCell.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(0);
        return c;
    }

    private static PdfPCell block(String label, List<String> lines, Font labelFont, Font strong, Font body) {
        PdfPCell c = new PdfPCell();
        c.setBorder(PdfPCell.NO_BORDER);
        c.setBackgroundColor(TINT);
        c.setPadding(9);
        c.addElement(new Paragraph(label, labelFont));
        boolean first = true;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Paragraph p = new Paragraph(line, first ? strong : body);
            p.setSpacingBefore(first ? 3 : 1);
            c.addElement(p);
            first = false;
        }
        return c;
    }

    private static void metaCell(PdfPTable t, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell c = new PdfPCell();
        c.setBorder(PdfPCell.TOP);
        c.setBorderColor(LINE);
        c.setPaddingTop(5);
        c.setPaddingBottom(2);
        c.addElement(new Paragraph(label, labelFont));
        c.addElement(new Paragraph(value, valueFont));
        t.addCell(c);
    }

    private static void extraMeta(PdfPTable meta, InvoiceType type, JsonNode snap, Font label, Font value) {
        switch (type) {
            case RECEIPT -> {
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
        a.setBorder(PdfPCell.BOTTOM);
        a.setBorderColor(LINE);
        a.setPaddingTop(4);
        a.setPaddingBottom(4);
        PdfPCell b = new PdfPCell(new Phrase(value, vf));
        b.setBorder(PdfPCell.BOTTOM);
        b.setBorderColor(LINE);
        b.setPaddingTop(4);
        b.setPaddingBottom(4);
        t.addCell(a);
        t.addCell(b);
    }

    private static void header(PdfPTable t, String s, int align, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(INK);
        c.setBorder(PdfPCell.NO_BORDER);
        c.setPadding(6);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String s, int align, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(bg);
        c.setBorder(PdfPCell.BOTTOM);
        c.setBorderColor(LINE);
        c.setPaddingTop(6);
        c.setPaddingBottom(6);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        t.addCell(c);
    }

    private static void totalRow(PdfPTable t, String label, String value, Font f, Color bg) {
        PdfPCell a = new PdfPCell(new Phrase(label, f));
        a.setBorder(PdfPCell.NO_BORDER);
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        a.setPadding(5);
        PdfPCell b = new PdfPCell(new Phrase(value, f));
        b.setBorder(PdfPCell.NO_BORDER);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        b.setPadding(5);
        if (bg != null) {
            a.setBackgroundColor(bg);
            b.setBackgroundColor(bg);
        }
        t.addCell(a);
        t.addCell(b);
    }
}
