package com.lms.invoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorDebtPayment;
import com.lms.finance.entity.InstructorPayout;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceLineRepository;
import com.lms.invoice.repository.InvoiceRepository;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phát hành chứng từ (20/11/2026). Mỗi hàm {@code issue*} chạy trong transaction riêng, IDEMPOTENT (đã có chứng từ cho nguồn đó thì trả về
 * {@link Optional#empty()}) và chụp toàn bộ dữ liệu cần hiển thị vào {@code snapshot_json} — đổi giá/tên khóa sau này không làm đổi chứng từ cũ.
 * Biên lai học viên CHỈ ghi giá, khuyến mãi, tổng đã trả; không ghi phí nền tảng/tỷ lệ chia (quyết định 15/11/2026).
 */
@Service
@RequiredArgsConstructor
public class InvoiceIssueService {

    static final int TEMPLATE_VERSION = 1;
    private static final String CFG_ISSUER_NAME = "INVOICE_ISSUER_NAME";
    private static final String CFG_ISSUER_NOTE = "INVOICE_ISSUER_NOTE";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;
    private final InvoiceNumberService numberService;
    private final PaymentRepository paymentRepository;
    private final SystemConfigService config;
    private final ObjectMapper objectMapper;

    /** Biên lai cho cả nhóm đơn của {@code anchor} (mỗi khóa một dòng). Đơn không còn khoản thu nào (vd tất cả 0đ) thì không phát hành. */
    @Transactional
    public Optional<Invoice> issueReceipt(Payment anchor) {
        String key = receiptKey(anchor);
        if (invoiceRepository.existsByTypeAndSourceKey(InvoiceType.RECEIPT, key)) {
            return Optional.empty();
        }
        List<Payment> group = anchor.getOrderGroupRef() == null
                ? List.of(anchor)
                : paymentRepository.findByOrderGroupRef(anchor.getOrderGroupRef());
        List<Payment> paid = group.stream()
                .filter(p -> (p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.REFUNDED)
                        && p.getPaidAt() != null && p.getAmount().signum() > 0)
                .sorted(Comparator.comparing(Payment::getId))
                .toList();
        if (paid.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        List<LineDraft> lines = new ArrayList<>();
        List<String> orders = new ArrayList<>();
        for (Payment p : paid) {
            BigDecimal gross = p.getOriginalAmount() != null && p.getOriginalAmount().compareTo(p.getAmount()) >= 0
                    ? p.getOriginalAmount() : p.getAmount();
            subtotal = subtotal.add(gross);
            total = total.add(p.getAmount());
            lines.add(new LineDraft(p.getCourse().getTitle(), 1, gross, gross));
            orders.add(p.getTxnRef());
        }
        LocalDateTime issuedAt = paid.stream().map(Payment::getPaidAt).max(Comparator.naturalOrder()).orElseThrow();
        User user = anchor.getUser();
        Payment first = paid.get(0);
        Map<String, Object> snap = baseSnapshot(user);
        snap.put("billingName", firstNonBlank(first.getBillingName(), user.getFullName()));
        snap.put("billingPhone", maskPhone(first.getBillingPhone()));
        snap.put("paymentMethod", first.getPaymentMethod());
        snap.put("gatewayTxnNo", first.getGatewayTxnNo());
        snap.put("orders", orders);
        return Optional.of(persist(InvoiceType.RECEIPT, user, key, null, null, subtotal, subtotal.subtract(total), total, issuedAt, snap, lines));
    }

    /** Phiếu hoàn tiền — chỉ phát hành khi tiền đã chuyển (có mã chuyển/ngày chuyển), gắn với biên lai gốc nếu đã có. */
    @Transactional
    public Optional<Invoice> issueRefund(PaymentRefund refund) {
        if (invoiceRepository.existsByTypeAndSourceId(InvoiceType.REFUND, refund.getId()) || refund.getTransferredAt() == null
                || refund.getAmount() == null || refund.getAmount().signum() <= 0) {
            return Optional.empty();
        }
        Payment payment = refund.getPayment();
        User user = payment.getUser();
        Invoice receipt = invoiceRepository.findByTypeAndSourceKey(InvoiceType.RECEIPT, receiptKey(payment)).orElse(null);
        Map<String, Object> snap = baseSnapshot(user);
        snap.put("orderRef", payment.getTxnRef());
        snap.put("transferRef", refund.getTransferRef());
        snap.put("transferredAt", refund.getTransferredAt().toString());
        snap.put("originalReceipt", receipt == null ? null : receipt.getNumber());
        List<LineDraft> lines = List.of(new LineDraft("Refund: " + payment.getCourse().getTitle() + " (order " + payment.getTxnRef() + ")",
                1, refund.getAmount(), refund.getAmount()));
        return Optional.of(persist(InvoiceType.REFUND, user, String.valueOf(refund.getId()), refund.getId(), receipt, refund.getAmount(), BigDecimal.ZERO, refund.getAmount(),
                refund.getTransferredAt(), snap, lines));
    }

    /** Phiếu chi cho yêu cầu rút tiền đã chi. Phí chuyển tiền do nền tảng chịu nên không trừ vào số tiền giảng viên nhận. */
    @Transactional
    public Optional<Invoice> issuePayout(InstructorPayout payout) {
        if (invoiceRepository.existsByTypeAndSourceId(InvoiceType.PAYOUT, payout.getId()) || payout.getAmount() == null
                || payout.getAmount().signum() <= 0) {
            return Optional.empty();
        }
        User user = payout.getInstructor();
        LocalDateTime issuedAt = payout.getPaidAt() != null ? payout.getPaidAt() : LocalDateTime.now();
        Map<String, Object> snap = baseSnapshot(user);
        snap.put("payoutCode", payout.getCode());
        snap.put("bankName", payout.getBankName());
        snap.put("accountMasked", "****" + payout.getAccountLast4());
        snap.put("holderName", payout.getHolderName());
        snap.put("transferRef", payout.getTransferRef());
        snap.put("feeNote", "Transfer fee is covered by the platform; nothing is deducted from your earnings.");
        List<LineDraft> lines = List.of(new LineDraft("Payout " + payout.getCode(), 1, payout.getAmount(), payout.getAmount()));
        return Optional.of(persist(InvoiceType.PAYOUT, user, String.valueOf(payout.getId()), payout.getId(), null, payout.getAmount(), BigDecimal.ZERO, payout.getAmount(), issuedAt, snap, lines));
    }

    /** Biên lai cho một lần giảng viên trả nợ qua PayOS thành công. */
    @Transactional
    public Optional<Invoice> issueDebtReceipt(InstructorDebtPayment payment) {
        if (invoiceRepository.existsByTypeAndSourceId(InvoiceType.DEBT_RECEIPT, payment.getId()) || payment.getAmount() == null
                || payment.getAmount() <= 0) {
            return Optional.empty();
        }
        User user = payment.getInstructor();
        BigDecimal amount = BigDecimal.valueOf(payment.getAmount());
        LocalDateTime issuedAt = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();
        Map<String, Object> snap = baseSnapshot(user);
        snap.put("orderCode", payment.getOrderCode());
        List<LineDraft> lines = List.of(new LineDraft("Debt repayment (payment code " + payment.getOrderCode() + ")", 1, amount, amount));
        return Optional.of(persist(InvoiceType.DEBT_RECEIPT, user, String.valueOf(payment.getId()), payment.getId(), null, amount, BigDecimal.ZERO, amount, issuedAt, snap, lines));
    }

    /** Dòng của bảng kê tháng: {@code amount} có dấu (thu nhập dương, hoàn/rút/điều chỉnh âm). */
    public record StatementLine(String description, BigDecimal amount) {}

    /**
     * Bảng kê thu nhập tháng của giảng viên, khóa nguồn {@code YYYYMM:instructorId} (mỗi giảng viên mỗi tháng đúng một bảng kê). {@code net} là tổng thay đổi
     * trong tháng. Chỉ phát hành một lần — dữ liệu tháng đã qua được chụp và không đổi.
     */
    @Transactional
    public Optional<Invoice> issueStatement(User instructor, String period, BigDecimal net, Map<String, Object> extra, List<StatementLine> lines) {
        String key = period + ":" + instructor.getId();
        if (invoiceRepository.existsByTypeAndSourceKey(InvoiceType.STATEMENT, key)) {
            return Optional.empty();
        }
        Map<String, Object> snap = baseSnapshot(instructor);
        snap.put("period", period);
        snap.putAll(extra);
        List<LineDraft> drafts = lines.stream().map(l -> new LineDraft(l.description(), 1, l.amount(), l.amount())).toList();
        return Optional.of(persist(InvoiceType.STATEMENT, instructor, key, null, null, net, BigDecimal.ZERO, net, LocalDateTime.now(), snap, drafts));
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** Mã truy cập 256 bit ngẫu nhiên, base64url không đệm (43 ký tự) — không thể đoán. */
    public static String newAccessToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String receiptKey(Payment p) {
        return p.getOrderGroupRef() != null ? p.getOrderGroupRef() : p.getTxnRef();
    }

    private Invoice persist(InvoiceType type, User party, String sourceKey, Long sourceId, Invoice related, BigDecimal subtotal, BigDecimal discount,
                            BigDecimal total, LocalDateTime issuedAt, Map<String, Object> snapshot, List<LineDraft> lines) {
        Invoice inv = new Invoice();
        inv.setNumber(numberService.next(type, issuedAt));
        inv.setType(type);
        inv.setParty(party);
        inv.setSourceKey(sourceKey);
        inv.setSourceId(sourceId);
        inv.setRelatedInvoice(related);
        inv.setSubtotal(subtotal);
        inv.setDiscount(discount);
        inv.setTotal(total);
        inv.setIssuedAt(issuedAt);
        inv.setTemplateVersion(TEMPLATE_VERSION);
        inv.setAccessToken(newAccessToken());
        inv.setSnapshotJson(toJson(snapshot));
        Invoice saved = invoiceRepository.save(inv);
        int no = 1;
        for (LineDraft d : lines) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(saved);
            line.setLineNo(no++);
            line.setDescription(truncate(d.description(), 500));
            line.setQuantity(d.quantity());
            line.setUnitPrice(d.unitPrice());
            line.setAmount(d.amount());
            lineRepository.save(line);
        }
        return saved;
    }

    private Map<String, Object> baseSnapshot(User party) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("issuerName", config.getString(CFG_ISSUER_NAME, "LinguaLearn"));
        snap.put("issuerNote", config.getString(CFG_ISSUER_NOTE, ""));
        snap.put("partyName", party.getFullName());
        snap.put("partyEmail", party.getEmail());
        return snap;
    }

    private String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được ảnh chụp chứng từ", e);
        }
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.trim();
        return digits.length() <= 4 ? digits : "****" + digits.substring(digits.length() - 4);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record LineDraft(String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {}
}
