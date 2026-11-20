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
import java.math.BigDecimal;
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
                .map(this::toRes).toList();
    }

    /** Chi tiết một chứng từ của chính mình (chủ chứng từ hoặc admin; người khác nhận 404). */
    @Transactional(readOnly = true)
    public InvoiceDto.Detail detail(String email, Long invoiceId) {
        User viewer = requireUser(email);
        Invoice inv = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        if (viewer.getRole() != Role.ADMIN && !inv.getParty().getId().equals(viewer.getId())) {
            throw new ResourceNotFoundException("Invoice", invoiceId);
        }
        return toDetail(inv);
    }

    private InvoiceDto.Detail toDetail(Invoice inv) {
        List<InvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(inv.getId());
        JsonNode snap = parse(inv.getSnapshotJson());
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        snap.fields().forEachRemaining(e -> {
            String k = e.getKey();
            if (!k.equals("partyEmail") && !k.equals("partyName") && !k.equals("issuerNote") && !e.getValue().isContainerNode()) {
                details.put(k, e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText());
            }
        });
        return new InvoiceDto.Detail(toAdminRes(inv), inv.getSubtotal(), inv.getDiscount(),
                lines.stream().map(l -> new InvoiceDto.Line(l.getLineNo(), l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getAmount())).toList(),
                details, breakdown(inv, lines));
    }

    /** Thu nhập theo khóa học của bảng kê tháng, rút ra từ các dòng "Earning - {khóa} (order ...)" đã chụp; các loại khác không có thành phần. */
    public static List<InvoiceDto.Slice> breakdown(Invoice inv, List<InvoiceLine> lines) {
        if (inv.getType() != InvoiceType.STATEMENT) {
            return List.of();
        }
        java.util.Map<String, java.math.BigDecimal> byCourse = new java.util.LinkedHashMap<>();
        for (InvoiceLine l : lines) {
            String d = l.getDescription();
            if (d.startsWith("Earning - ")) {
                int end = d.indexOf(" (order ");
                String course = (end > 0 ? d.substring("Earning - ".length(), end) : d.substring("Earning - ".length())).trim();
                byCourse.merge(course, l.getAmount(), java.math.BigDecimal::add);
            }
        }
        return byCourse.entrySet().stream().map(e -> new InvoiceDto.Slice(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.amount().compareTo(a.amount())).toList();
    }

    /** Admin xem chi tiết bất kỳ chứng từ. */
    @Transactional(readOnly = true)
    public InvoiceDto.Detail adminDetail(Long invoiceId) {
        return toDetail(invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId)));
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

    /** Dữ liệu để gửi email chứng từ (không kiểm quyền — chỉ job nội bộ gọi, người nhận luôn là chủ chứng từ). */
    @Transactional(readOnly = true)
    public MailPayload mailPayload(Long invoiceId) {
        Invoice inv = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        List<InvoiceLine> lines = lineRepository.findByInvoice_IdOrderByLineNoAsc(inv.getId());
        byte[] bytes = InvoicePdfRenderer.render(inv, lines, parse(inv.getSnapshotJson()));
        return new MailPayload(inv.getParty().getEmail(), inv.getParty().getFullName(), inv.getType(), inv.getNumber(), inv.getTotal(),
                inv.getIssuedAt(), inv.getNumber() + ".pdf", bytes);
    }

    @Transactional(readOnly = true)
    public InvoiceDto.AdminPage search(InvoiceType type, LocalDate from, LocalDate to, String q, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Tìm-kiếm-trước: không có điều kiện nào thì không trả dữ liệu (không liệt kê toàn bộ chứng từ).
        if (type == null && from == null && to == null && like(q) == null) {
            return new InvoiceDto.AdminPage(List.of(), 0, 0, safeSize);
        }
        Page<Invoice> result = invoiceRepository.search(type, start(from), end(to), like(q), qId(q), PageRequest.of(Math.max(0, page), safeSize));
        return new InvoiceDto.AdminPage(result.getContent().stream().map(this::toAdminRes).toList(),
                result.getTotalElements(), result.getNumber(), safeSize);
    }

    /** CSV (UTF-8, có BOM để Excel đọc đúng tiếng Việt), tối đa {@link #MAX_EXPORT_ROWS} dòng theo đúng bộ lọc đang xem. */
    @Transactional(readOnly = true)
    public String exportCsv(InvoiceType type, LocalDate from, LocalDate to, String q) {
        if (type == null && from == null && to == null && like(q) == null) {
            throw new com.lms.common.exception.InvalidRequestException("Hãy chọn loại chứng từ, khoảng ngày hoặc nhập từ khóa trước khi xuất");
        }
        List<Invoice> rows = invoiceRepository.search(type, start(from), end(to), like(q), qId(q), PageRequest.of(0, MAX_EXPORT_ROWS)).getContent();
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

    private static long qId(String q) {
        return q != null && q.trim().matches("\\d{1,18}") ? Long.parseLong(q.trim()) : -1L;
    }

    private static LocalDateTime start(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay();
    }

    private InvoiceDto.Res toRes(Invoice i) {
        return new InvoiceDto.Res(i.getId(), i.getNumber(), i.getType(), i.getStatus(), i.getCurrency(), i.getSubtotal(), i.getDiscount(),
                i.getTotal(), i.getIssuedAt(), i.getRelatedInvoice() == null ? null : i.getRelatedInvoice().getNumber(), null, refs(i));
    }

    private InvoiceDto.AdminRes toAdminRes(Invoice i) {
        return new InvoiceDto.AdminRes(i.getId(), i.getNumber(), i.getType(), i.getStatus(), i.getCurrency(), i.getTotal(), i.getIssuedAt(),
                i.getParty().getId(), i.getParty().getFullName(), i.getParty().getEmail(),
                i.getRelatedInvoice() == null ? null : i.getRelatedInvoice().getNumber(), refs(i));
    }

    /** Mã nghiệp vụ trong ảnh chụp của chứng từ (xem {@link InvoiceDto.Res}). Ảnh chụp hỏng thì trả danh sách rỗng, không làm vỡ cả danh sách. */
    private List<String> refs(Invoice i) {
        try {
            JsonNode snap = objectMapper.readTree(i.getSnapshotJson());
            return switch (i.getType()) {
                case RECEIPT -> {
                    List<String> out = new java.util.ArrayList<>();
                    if (snap.has("orders")) {
                        snap.get("orders").forEach(n -> out.add(n.asText()));
                    }
                    yield out;
                }
                case REFUND -> snap.hasNonNull("orderRef") ? List.of(snap.get("orderRef").asText()) : List.of();
                case PAYOUT -> snap.hasNonNull("payoutCode") ? List.of(snap.get("payoutCode").asText()) : List.of();
                case DEBT_RECEIPT -> snap.hasNonNull("orderCode") ? List.of(snap.get("orderCode").asText()) : List.of();
                case STATEMENT -> snap.hasNonNull("period") ? List.of(snap.get("period").asText()) : List.of();
            };
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public record PdfFile(String filename, byte[] bytes) {
    }

    public record MailPayload(String toEmail, String recipientName, InvoiceType type, String number, BigDecimal total,
                              LocalDateTime issuedAt, String filename, byte[] pdf) {
    }
}
