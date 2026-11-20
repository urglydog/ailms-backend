package com.lms.invoice.dto;

import com.lms.invoice.entity.InvoiceStatus;
import com.lms.invoice.entity.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO chứng từ — không lộ entity; snapshot/dòng chi tiết chỉ nằm trong PDF, danh sách chỉ cần phần tóm tắt. */
public final class InvoiceDto {

    private InvoiceDto() {}

    public record Res(Long id, String number, InvoiceType type, InvoiceStatus status, String currency, BigDecimal subtotal,
                      BigDecimal discount, BigDecimal total, LocalDateTime issuedAt, String relatedNumber, String description) {}

    public record AdminRes(Long id, String number, InvoiceType type, InvoiceStatus status, String currency, BigDecimal total,
                           LocalDateTime issuedAt, Long partyUserId, String partyName, String partyEmail, String relatedNumber) {}

    public record AdminPage(List<AdminRes> items, long total, int page, int size) {}
}
