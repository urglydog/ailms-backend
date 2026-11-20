package com.lms.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorDebtPayment;
import com.lms.finance.entity.InstructorPayout;
import com.lms.invoice.entity.Invoice;
import com.lms.invoice.entity.InvoiceLine;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceLineRepository;
import com.lms.invoice.repository.InvoiceRepository;
import com.lms.invoice.service.InvoiceIssueService;
import com.lms.invoice.service.InvoiceNumberService;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceIssueServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository lineRepository;
    @Mock private InvoiceNumberService numberService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SystemConfigService config;

    private InvoiceIssueService service;
    private User learner;

    @BeforeEach
    void setUp() {
        service = new InvoiceIssueService(invoiceRepository, lineRepository, numberService, paymentRepository, config, new ObjectMapper());
        learner = new User();
        learner.setId(7L);
        learner.setEmail("hocvien@gmail.com");
        learner.setFullName("Nguyễn Văn Ánh");
        lenient().when(config.getString(any(), any())).thenAnswer(i -> i.getArgument(1));
        lenient().when(numberService.next(any(), any())).thenReturn("X-000001");
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(100L);
            return inv;
        });
    }

    private Payment payment(long id, String txn, String group, String title, String original, String amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setTxnRef(txn);
        p.setOrderGroupRef(group);
        p.setUser(learner);
        Course c = new Course();
        c.setTitle(title);
        p.setCourse(c);
        p.setOriginalAmount(original == null ? null : new BigDecimal(original));
        p.setAmount(new BigDecimal(amount));
        p.setStatus(status);
        p.setPaidAt(LocalDateTime.of(2026, 11, 18, 9, (int) id));
        p.setPaymentMethod("PAYOS");
        p.setGatewayTxnNo("GW" + id);
        p.setBillingPhone("0901234567");
        return p;
    }

    @Test
    void issueReceipt_groupsPaidOrdersOfTheCartIntoOneInvoiceWithDiscountTotals() {
        Payment a = payment(1, "T1", "G1", "Java", "100000", "80000", PaymentStatus.PAID);
        Payment b = payment(2, "T2", "G1", "Unity", null, "50000", PaymentStatus.PAID);
        Payment failed = payment(3, "T3", "G1", "Rust", "70000", "70000", PaymentStatus.FAILED);
        when(invoiceRepository.existsByTypeAndSourceKey(InvoiceType.RECEIPT, "G1")).thenReturn(false);
        when(paymentRepository.findByOrderGroupRef("G1")).thenReturn(List.of(b, failed, a));

        Optional<Invoice> result = service.issueReceipt(a);

        assertThat(result).isPresent();
        Invoice inv = result.get();
        assertThat(inv.getSubtotal()).isEqualByComparingTo("150000");
        assertThat(inv.getDiscount()).isEqualByComparingTo("20000");
        assertThat(inv.getTotal()).isEqualByComparingTo("130000");
        assertThat(inv.getIssuedAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 2));
        assertThat(inv.getSourceKey()).isEqualTo("G1");
        // Biên lai học viên không được lộ phí nền tảng/tỷ lệ chia; số điện thoại chỉ còn 4 số cuối.
        assertThat(inv.getSnapshotJson()).doesNotContain("platform").doesNotContain("Rate").contains("****4567").doesNotContain("0901234567");
        ArgumentCaptor<InvoiceLine> lines = ArgumentCaptor.forClass(InvoiceLine.class);
        verify(lineRepository, org.mockito.Mockito.times(2)).save(lines.capture());
        assertThat(lines.getAllValues()).extracting(InvoiceLine::getDescription).containsExactly("Java", "Unity");
    }

    @Test
    void issueReceipt_isIdempotentWhenInvoiceAlreadyExists() {
        Payment a = payment(1, "T1", null, "Java", null, "50000", PaymentStatus.PAID);
        when(invoiceRepository.existsByTypeAndSourceKey(InvoiceType.RECEIPT, "T1")).thenReturn(true);

        assertThat(service.issueReceipt(a)).isEmpty();

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void issueReceipt_skipsFreeOrders() {
        Payment free = payment(1, "T1", null, "Free course", null, "0", PaymentStatus.PAID);
        when(invoiceRepository.existsByTypeAndSourceKey(InvoiceType.RECEIPT, "T1")).thenReturn(false);

        assertThat(service.issueReceipt(free)).isEmpty();

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void issueRefund_linksOriginalReceiptAndRequiresTransferDate() {
        Payment p = payment(1, "T1", null, "Java", null, "50000", PaymentStatus.REFUNDED);
        PaymentRefund r = new PaymentRefund();
        r.setId(9L);
        r.setPayment(p);
        r.setAmount(new BigDecimal("50000"));
        r.setTransferRef("PAYOS-ABC");
        Invoice receipt = new Invoice();
        receipt.setNumber("RCP-202611-000007");
        when(invoiceRepository.existsByTypeAndSourceId(InvoiceType.REFUND, 9L)).thenReturn(false);

        // Chưa chuyển tiền (chưa có ngày chuyển) → chưa phát hành.
        assertThat(service.issueRefund(r)).isEmpty();

        r.setTransferredAt(LocalDateTime.of(2026, 11, 19, 8, 0));
        when(invoiceRepository.findByTypeAndSourceKey(InvoiceType.RECEIPT, "T1")).thenReturn(Optional.of(receipt));
        Invoice inv = service.issueRefund(r).orElseThrow();

        assertThat(inv.getTotal()).isEqualByComparingTo("50000");
        assertThat(inv.getRelatedInvoice()).isSameAs(receipt);
        assertThat(inv.getSnapshotJson()).contains("RCP-202611-000007").contains("PAYOS-ABC");
    }

    @Test
    void issuePayout_masksAccountAndStatesTheFeeIsNotDeducted() {
        User instructor = new User();
        instructor.setId(3L);
        instructor.setEmail("gv@gmail.com");
        instructor.setFullName("Giảng Viên");
        InstructorPayout payout = new InstructorPayout();
        payout.setId(5L);
        payout.setCode("PO-202611-000001");
        payout.setInstructor(instructor);
        payout.setAmount(new BigDecimal("20000"));
        payout.setBankName("MB Bank");
        payout.setAccountLast4("1234");
        payout.setHolderName("GIANG VIEN");
        payout.setTransferRef("FT123");
        payout.setPaidAt(LocalDateTime.of(2026, 11, 19, 10, 0));
        when(invoiceRepository.existsByTypeAndSourceId(InvoiceType.PAYOUT, 5L)).thenReturn(false);

        Invoice inv = service.issuePayout(payout).orElseThrow();

        assertThat(inv.getType()).isEqualTo(InvoiceType.PAYOUT);
        assertThat(inv.getSnapshotJson()).contains("****1234").contains("covered by the platform").doesNotContain("2950").doesNotContain("2.950");
        assertThat(inv.getTotal()).isEqualByComparingTo("20000");
    }

    @Test
    void issueDebtReceipt_usesPaidAtAsDocumentDate() {
        User instructor = new User();
        instructor.setId(3L);
        instructor.setEmail("gv@gmail.com");
        instructor.setFullName("GV");
        InstructorDebtPayment d = new InstructorDebtPayment();
        d.setId(4L);
        d.setInstructor(instructor);
        d.setAmount(30000L);
        d.setOrderCode(1_000_000_000_123L);
        d.setPaidAt(LocalDateTime.of(2026, 11, 17, 12, 0));
        when(invoiceRepository.existsByTypeAndSourceId(InvoiceType.DEBT_RECEIPT, 4L)).thenReturn(false);

        Invoice inv = service.issueDebtReceipt(d).orElseThrow();

        assertThat(inv.getIssuedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 12, 0));
        assertThat(inv.getTotal()).isEqualByComparingTo("30000");
    }
}
