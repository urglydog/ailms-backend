package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.service.EnrollmentService;
import com.lms.payment.dto.PaymentDto.CreateBatchReq;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc.
 * Kiểm tra {@code createBatchPayment} (gộp thanh toán nhiều khóa qua 1 {@code orderGroupRef}
 * dùng chung, mỗi khóa vẫn 1 {@link Payment} riêng — BR-PAY-05) và {@code processIpn} phiên
 * bản nhận biết NHÓM (xác nhận CẢ NHÓM Payment cùng lúc khi cổng gọi lại đúng 1 lần).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String EMAIL = "student@lms.local";

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentService enrollmentService;
    @Mock private PayOS payOS;
    @Mock private PaymentRequestsService paymentRequestsService;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Course courseA;
    private Course courseB;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        courseA = new Course();
        courseA.setId(10L);
        courseA.setTitle("Unity co ban");
        courseA.setStatus(CourseStatus.PUBLISHED);
        courseA.setIsFree(false);
        courseA.setPrice(new BigDecimal("200000"));

        courseB = new Course();
        courseB.setId(20L);
        courseB.setTitle("Blender nang cao");
        courseB.setStatus(CourseStatus.PUBLISHED);
        courseB.setIsFree(false);
        courseB.setPrice(new BigDecimal("300000"));

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(courseA));
        lenient().when(courseRepository.findById(20L)).thenReturn(Optional.of(courseB));
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        lenient().when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 20L)).thenReturn(false);
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.setField(paymentService, "vnpTmnCode", "TEST_TMN");
        ReflectionTestUtils.setField(paymentService, "vnpHashSecret", "test-secret");
        ReflectionTestUtils.setField(paymentService, "vnpUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "vnpReturnUrl", "http://localhost:3000/payments/callback");
    }

    // ── createBatchPayment ─────────────────────────────────────────────

    @Test
    void createBatchPayment_payos_createsOnePaymentPerCourseWithSharedGroupRefAndDistinctTxnRef() {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);
        // CreatePaymentLinkResponse dung Lombok @NonNull tren moi field cua constructor day
        // du ma builder() goi ben trong — phai truyen du gia tri, khong chi rieng checkoutUrl.
        when(paymentRequestsService.create(any())).thenReturn(
                CreatePaymentLinkResponse.builder()
                        .bin("970422")
                        .accountNumber("0000000000")
                        .accountName("LMS TEST")
                        .amount(500000L)
                        .description("Thanh toan test")
                        .orderCode(123456789L)
                        .currency("VND")
                        .paymentLinkId("test-link-id")
                        .status(PaymentLinkStatus.PENDING)
                        .expiredAt(0L)
                        .checkoutUrl("https://payos.example/checkout/abc")
                        .qrCode("test-qr")
                        .build());

        var result = paymentService.createBatchPayment(
                EMAIL, new CreateBatchReq(List.of(10L, 20L), "PAYOS", "Nguyen Van A", "0900000000"));

        assertThat(result.paymentUrl()).isEqualTo("https://payos.example/checkout/abc");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        List<Payment> saved = captor.getAllValues();
        assertThat(saved).extracting(Payment::getCourse).containsExactlyInAnyOrder(courseA, courseB);
        assertThat(saved).extracting(Payment::getAmount).containsExactlyInAnyOrder(new BigDecimal("200000"), new BigDecimal("300000"));
        assertThat(saved).extracting(Payment::getStatus).containsOnly(PaymentStatus.PENDING);
        // Chung 1 orderGroupRef (ca nhom), nhung txnRef TUNG dong van khac nhau (giu dung
        // rang buoc UNIQUE cu tren cot txn_ref).
        assertThat(saved.get(0).getOrderGroupRef()).isEqualTo(saved.get(1).getOrderGroupRef());
        assertThat(saved.get(0).getTxnRef()).isNotEqualTo(saved.get(1).getTxnRef());
    }

    @Test
    void createBatchPayment_oneCourseIsFree_throwsWithoutSavingAnyPayment() {
        courseB.setIsFree(true);

        assertThatThrownBy(() -> paymentService.createBatchPayment(
                EMAIL, new CreateBatchReq(List.of(10L, 20L), "PAYOS", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createBatchPayment_oneCourseAlreadyOwned_throwsWithoutSavingAnyPayment() {
        when(enrollmentRepository.existsByUser_IdAndCourse_Id(1L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createBatchPayment(
                EMAIL, new CreateBatchReq(List.of(10L, 20L), "PAYOS", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createBatchPayment_emptyCourseList_throws() {
        assertThatThrownBy(() -> paymentService.createBatchPayment(EMAIL, new CreateBatchReq(List.of(), "PAYOS", null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void createBatchPayment_vnpay_returnsBuiltUrlForTotalAmount() {
        var result = paymentService.createBatchPayment(
                EMAIL, new CreateBatchReq(List.of(10L, 20L), "VNPAY", null, null));

        assertThat(result.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(result.paymentUrl()).contains("vnp_Amount=50000000"); // (200000+300000) * 100
    }

    // ── processIpn (nhận biết nhóm) ─────────────────────────────────────

    @Test
    void processIpn_singlePaymentNoGroup_marksPaidAndCreatesEnrollment_unchangedBehavior() {
        Payment payment = pendingPayment("txn-1", null, courseA, new BigDecimal("200000"));
        when(paymentRepository.findByOrderGroupRef("txn-1")).thenReturn(List.of());
        when(paymentRepository.findByTxnRef("txn-1")).thenReturn(Optional.of(payment));

        paymentService.processIpn("txn-1", "GW-1", true);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPlatformFee()).isEqualByComparingTo("60000");
        assertThat(payment.getInstructorEarning()).isEqualByComparingTo("140000");
        verify(enrollmentService).createFromPayment(payment);
    }

    @Test
    void processIpn_groupOfMultiplePayments_marksAllPaidAndCreatesEnrollmentForEach() {
        Payment p1 = pendingPayment("txn-1", "GROUP-1", courseA, new BigDecimal("200000"));
        Payment p2 = pendingPayment("txn-2", "GROUP-1", courseB, new BigDecimal("300000"));
        when(paymentRepository.findByOrderGroupRef("GROUP-1")).thenReturn(List.of(p1, p2));

        paymentService.processIpn("GROUP-1", "GW-GROUP", true);

        assertThat(p1.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p2.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(enrollmentService).createFromPayment(p1);
        verify(enrollmentService).createFromPayment(p2);
    }

    @Test
    void processIpn_alreadyPaid_isIdempotentAndDoesNotReprocess() {
        Payment payment = pendingPayment("txn-1", null, courseA, new BigDecimal("200000"));
        payment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByOrderGroupRef("txn-1")).thenReturn(List.of());
        when(paymentRepository.findByTxnRef("txn-1")).thenReturn(Optional.of(payment));

        paymentService.processIpn("txn-1", "GW-1", true);

        verify(enrollmentService, never()).createFromPayment(any());
    }

    @Test
    void processIpn_notFound_throws() {
        when(paymentRepository.findByOrderGroupRef("missing")).thenReturn(List.of());
        when(paymentRepository.findByTxnRef("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processIpn("missing", "GW", true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void processIpn_failure_marksFailedWithoutCreatingEnrollment() {
        Payment payment = pendingPayment("txn-1", null, courseA, new BigDecimal("200000"));
        when(paymentRepository.findByOrderGroupRef("txn-1")).thenReturn(List.of());
        when(paymentRepository.findByTxnRef("txn-1")).thenReturn(Optional.of(payment));

        paymentService.processIpn("txn-1", "GW-1", false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(enrollmentService, never()).createFromPayment(any());
    }

    private Payment pendingPayment(String txnRef, String orderGroupRef, Course course, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setTxnRef(txnRef);
        payment.setOrderGroupRef(orderGroupRef);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(user);
        payment.setCourse(course);
        return payment;
    }
}
