package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceStatus;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.util.InvoicePdfRenderer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePdfRendererTest {

    private static Invoice invoice(InvoiceType type, String discount) {
        Invoice i = new Invoice();
        i.setNumber(type.prefix() + "-202611-000001");
        i.setType(type);
        i.setStatus(InvoiceStatus.ISSUED);
        i.setSubtotal(new BigDecimal("100000").add(new BigDecimal(discount)));
        i.setDiscount(new BigDecimal(discount));
        i.setTotal(new BigDecimal("100000"));
        i.setIssuedAt(LocalDateTime.of(2026, 11, 20, 9, 30));
        return i;
    }

    private static InvoiceLine line(String description) {
        InvoiceLine l = new InvoiceLine();
        l.setDescription(description);
        l.setQuantity(1);
        l.setUnitPrice(new BigDecimal("100000"));
        l.setAmount(new BigDecimal("100000"));
        return l;
    }

    @Test
    void render_producesAPdfForEveryTypeIncludingVietnameseNames() throws Exception {
        String snap = "{\"issuerName\":\"LinguaLearn\",\"issuerNote\":\"Graduation project\",\"partyName\":\"Nguyễn Thị Hương Giang\","
                + "\"partyEmail\":\"a@b.com\",\"billingName\":\"Trần Đình Ưu\",\"billingPhone\":\"****4567\",\"orders\":[\"T1\",\"T2\"],"
                + "\"feeNote\":\"Transfer fee is covered by the platform.\"}";
        for (InvoiceType type : InvoiceType.values()) {
            byte[] pdf = InvoicePdfRenderer.render(invoice(type, "0"), List.of(line("Khóa học Tiếng Nhật N3 – Ngữ pháp")),
                    new ObjectMapper().readTree(snap));
            assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
            assertThat(pdf.length).isGreaterThan(2000);
        }
    }

    @Test
    void render_handlesDiscountAndVoidedStatus() throws Exception {
        Invoice inv = invoice(InvoiceType.RECEIPT, "20000");
        inv.setStatus(InvoiceStatus.VOIDED);

        byte[] pdf = InvoicePdfRenderer.render(inv, List.of(line("Java")), new ObjectMapper().readTree("{}"));

        assertThat(pdf.length).isGreaterThan(2000);
    }

    @Test
    void money_usesVietnameseGroupingWithoutDecimals() {
        assertThat(InvoicePdfRenderer.money(new BigDecimal("1234567.00"))).isEqualTo("1.234.567 VND");
        assertThat(InvoicePdfRenderer.money(BigDecimal.ZERO)).isEqualTo("0 VND");
    }

    @Test
    void render_withQrCodeAndChecksumProducesALargerPdfThanWithout() throws Exception {
        String snap = "{\"issuerName\":\"LinguaLearn\",\"issuerNote\":\"Graduation project\",\"partyName\":\"Nguyễn Văn A\",\"partyEmail\":\"a@b.com\","
                + "\"billingName\":\"Công ty TNHH Đào tạo\",\"billingPhone\":\"****4567\",\"paymentMethod\":\"PAYOS\",\"gatewayTxnNo\":\"GW1\",\"orders\":[\"T1\"]}";
        var json = new ObjectMapper().readTree(snap);
        byte[] plain = InvoicePdfRenderer.render(invoice(InvoiceType.RECEIPT, "0"), List.of(line("Khóa học Tiếng Nhật N3")), json, null, "9F3A-12BC-77D0-E4A1");
        byte[] withQr = InvoicePdfRenderer.render(invoice(InvoiceType.RECEIPT, "0"), List.of(line("Khóa học Tiếng Nhật N3")), json,
                "https://example.test/api/v1/public/invoices/" + "A".repeat(43), "9F3A-12BC-77D0-E4A1");

        assertThat(new String(withQr, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(withQr.length).isGreaterThan(plain.length);
    }
}
