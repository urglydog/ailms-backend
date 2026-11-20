package com.lms.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.invoice.dto.InvoiceDto;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceLineRepository;
import com.lms.invoice.repository.InvoiceRepository;
import com.lms.invoice.util.InvoicePdfRenderer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc/tải chứng từ. Người dùng chỉ thấy chứng từ của chính mình; admin xem được tất cả (kể cả tải PDF). */
@Service
@RequiredArgsConstructor
public class InvoiceQueryService {

    static final int MAX_MINE = 200;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 5000;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<InvoiceDto.Res> listMine(String email) {
        User user = requireUser(email);
        return invoiceRepository.findByParty_IdOrderByIssuedAtDescIdDesc(user.getId(), PageRequest.of(0, MAX_MINE)).stream()
                .map(InvoiceQueryService::toRes).toList();
    }

    /** Tải PDF: chủ chứng từ hoặc admin; người khác nhận 404 (không tiết lộ chứng từ có tồn tại hay không). */
    @Transactional(readOnly = true)
    public PdfFile pdf(String email, Long invoiceId) {
        User viewer = requireUser(email);
        Invoice inv = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        if (viewer.getRole() != Role.ADMIN && !inv.getParty().getId().equals(viewer.getId())) {
            throw new ResourceNotFoundException("Invoice", invoiceId);
        }
        List<InvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(inv.getId());
        byte[] bytes = InvoicePdfRenderer.render(inv, lines, parse(inv.getSnapshotJson()));
        return new PdfFile(inv.getNumber() + ".pdf", bytes);
    }

    @Transactional(readOnly = true)
    public InvoiceDto.AdminPage search(InvoiceType type, LocalDate from, LocalDate to, String q, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<Invoice> result = invoiceRepository.search(type, start(from), end(to), like(q), PageRequest.of(Math.max(0, page), safeSize));
        return new InvoiceDto.AdminPage(result.getContent().stream().map(InvoiceQueryService::toAdminRes).toList(),
                result.getTotalElements(), result.getNumber(), safeSize);
    }

    /** CSV (UTF-8, có BOM để Excel đọc đúng tiếng Việt), tối đa {@link #MAX_EXPORT_ROWS} dòng theo đúng bộ lọc đang xem. */
    @Transactional(readOnly = true)
    public String exportCsv(InvoiceType type, LocalDate from, LocalDate to, String q) {
        List<Invoice> rows = invoiceRepository.search(type, start(from), end(to), like(q), PageRequest.of(0, MAX_EXPORT_ROWS)).getContent();
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("number,type,status,issued_at,party_name,party_email,currency,total,related_number\n");
        for (Invoice i : rows) {
            sb.append(csv(i.getNumber())).append(',').append(i.getType()).append(',').append(i.getStatus()).append(',')
                    .append(i.getIssuedAt()).append(',').append(csv(i.getParty().getFullName())).append(',').append(csv(i.getParty().getEmail()))
                    .append(',').append(i.getCurrency()).append(',').append(i.getTotal().toPlainString()).append(',')
                    .append(csv(i.getRelatedInvoice() == null ? "" : i.getRelatedInvoice().getNumber())).append('\n');
        }
        return sb.toString();
    }

    /** Chống CSV/formula injection: ô bắt đầu bằng = + - @ hoặc tab/CR được đặt thêm dấu nháy đơn phía trước; luôn bọc ngoặc kép. */
    public static String csv(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ảnh chụp chứng từ hỏng", e);
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new AccessDeniedDomainException("Không xác định được người dùng"));
    }

    private static String like(String q) {
        return q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
    }

    private static LocalDateTime start(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay();
    }

    private static InvoiceDto.Res toRes(Invoice i) {
        return new InvoiceDto.Res(i.getId(), i.getNumber(), i.getType(), i.getStatus(), i.getCurrency(), i.getSubtotal(), i.getDiscount(),
                i.getTotal(), i.getIssuedAt(), i.getRelatedInvoice() == null ? null : i.getRelatedInvoice().getNumber(), null);
    }

    private static InvoiceDto.AdminRes toAdminRes(Invoice i) {
        return new InvoiceDto.AdminRes(i.getId(), i.getNumber(), i.getType(), i.getStatus(), i.getCurrency(), i.getTotal(), i.getIssuedAt(),
                i.getParty().getId(), i.getParty().getFullName(), i.getParty().getEmail(),
                i.getRelatedInvoice() == null ? null : i.getRelatedInvoice().getNumber());
    }

    public record PdfFile(String filename, byte[] bytes) {
    }
}
