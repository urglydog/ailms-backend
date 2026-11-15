package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RefundReasonCode;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.util.CacheEvictionHelper;
import com.lms.enrollment.service.CourseReviewService;
import com.lms.enrollment.service.EnrollmentService;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentRefundRepository;
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

/** Khóa 1.000.000đ organic 37/63: giảng viên đã nhận 370.000, nền tảng 630.000. */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentRefundRepository refundRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentService enrollmentService;
    @Mock private CourseReviewService courseReviewService;
    @Mock private LedgerService ledgerService;
    @Mock private FinanceAuditService financeAuditService;
    @Mock private CacheEvictionHelper cacheEvictionHelper;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @Mock private com.lms.common.service.SystemConfigService systemConfigService;
    @InjectMocks
    private RefundService refundService;

    private User admin;
    private User learner;
    private User instructor;
    private Course course;
    private Payment payment;
    private PaymentDispute dispute;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        learner = new User();
        learner.setId(2L);
        learner.setEmail("hocvien@lms.local");
        learner.setFullName("Hoc Vien");

        instructor = new User();
        instructor.setId(3L);

        course = new Course();
        course.setId(10L);
        course.setSlug("unity");
        course.setTitle("Unity co ban");
        course.setInstructor(instructor);

        payment = new Payment();
        payment.setId(100L);
        payment.setTxnRef("txn-100");
        payment.setUser(learner);
        payment.setCourse(course);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("1000000"));
        payment.setPlatformFee(new BigDecimal("630000"));
        payment.setInstructorEarning(new BigDecimal("370000"));

        dispute = new PaymentDispute();
        dispute.setId(500L);
        dispute.setPayment(payment);
        dispute.setFiledBy(learner);
        dispute.setRefundBankAccount("VCB 0123456789 HOC VIEN");

        lenient().when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(refundRepository.save(any(PaymentRefund.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(refundRepository.countByPayment_User_IdAndApprovedAtAfter(eq(2L), any())).thenReturn(1L);
    }

    @Test
    void approve_standard_refundsFullAmountAndChargesInstructorOnlyWhatTheyReceived() {
        PaymentRefund refund = refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD, null, "đổi ý", null);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
        assertThat(refund.getAmount()).isEqualByComparingTo("1000000");
        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("370000");
        assertThat(refund.getPlatformShare()).isEqualByComparingTo("630000");
        assertThat(refund.getResponsibility()).isEqualTo(RefundResponsibility.STANDARD);
        assertThat(refund.getTransferStatus()).isEqualTo(RefundTransferStatus.PENDING_TRANSFER);
        assertThat(refund.getRefundBankInfo()).isEqualTo("VCB 0123456789 HOC VIEN");
        assertThat(refund.getApprovedBy()).isSameAs(admin);

        verify(enrollmentService).revokeForRefund(eq(payment), anyString());
        verify(courseReviewService).hideForRefund(2L, 10L);
        verify(cacheEvictionHelper).evictCourseCacheAfterCommit("unity");
        ArgumentCaptor<BigDecimal> charge = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledgerService).recordRefundReversal(eq(payment), charge.capture(), eq(1L));
        assertThat(charge.getValue()).isEqualByComparingTo("370000");
        verify(financeAuditService).record(eq(ADMIN_EMAIL), eq("REFUND_APPROVED"), eq("payment"), eq("txn-100"), any(), any());
        verify(notificationService).notify(eq(3L), eq("REFUND_APPROVED_INSTRUCTOR"), eq("Thu nhập của bạn bị trừ do học viên được hoàn tiền"),
                org.mockito.ArgumentMatchers.contains("bị trừ 370.000đ"), anyString());
    }

    @Test
    void approve_nullResponsibility_defaultsToStandard() {
        PaymentRefund refund = refundService.approve(dispute, ADMIN_EMAIL, null, null, "ok", null);

        assertThat(refund.getResponsibility()).isEqualTo(RefundResponsibility.STANDARD);
        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("370000");
    }

    @Test
    void approve_platformCompensates_instructorKeepsEarning() {
        PaymentRefund refund = refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.PLATFORM_COMPENSATES, RefundReasonCode.TECHNICAL_ISSUE, "sự cố hệ thống", null);

        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("0");
        assertThat(refund.getPlatformShare()).isEqualByComparingTo("1000000");
        ArgumentCaptor<BigDecimal> charge = ArgumentCaptor.forClass(BigDecimal.class);
        verify(ledgerService).recordRefundReversal(eq(payment), charge.capture(), eq(1L));
        assertThat(charge.getValue()).isEqualByComparingTo("0");
    }

    @Test
    void approve_instructorFault_instructorRepaysWholeAmount() {
        PaymentRefund refund = refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.INSTRUCTOR_FAULT, RefundReasonCode.CONTENT_MISMATCH,
                "mô tả sai nghiêm trọng", "ảnh chụp nội dung bài giảng khác hẳn mô tả");

        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("1000000");
        assertThat(refund.getPlatformShare()).isEqualByComparingTo("0");
    }

    @Test
    void approve_instructorFaultWithoutReason_isRejectedWithoutAnySideEffect() {
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.INSTRUCTOR_FAULT, RefundReasonCode.CONTENT_MISMATCH, "  ", "ảnh chụp nội dung bài giảng khác hẳn mô tả"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(enrollmentService, never()).revokeForRefund(any(), any());
        verify(ledgerService, never()).recordRefundReversal(any(), any(), any());
        verify(refundRepository, never()).save(any(PaymentRefund.class));
    }

    @Test
    void approve_alreadyRefunded_returnsExistingRecordAndDoesNothingElse() {
        PaymentRefund existing = new PaymentRefund();
        when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.of(existing));

        PaymentRefund result = refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD, null, "lần 2", null);

        assertThat(result).isSameAs(existing);
        verify(paymentRepository, never()).findByIdForUpdate(any());
        verify(enrollmentService, never()).revokeForRefund(any(), any());
        verify(ledgerService, never()).recordRefundReversal(any(), any(), any());
    }

    @Test
    void approve_paymentNotPaid_orZeroAmount_isRejected() {
        payment.setStatus(PaymentStatus.PENDING);
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD, null, "x", null))
                .isInstanceOf(BusinessRuleViolationException.class);

        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD, null, "x", null))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(refundRepository, never()).save(any(PaymentRefund.class));
    }

    @Test
    void approve_learnerRefundedManyTimes_alertsAdmins() {
        when(refundRepository.countByPayment_User_IdAndApprovedAtAfter(eq(2L), any())).thenReturn(3L);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD, null, "x", null);

        verify(notificationService).notify(eq(1L), eq("REFUND_ABUSE_SUSPECT"), anyString(), anyString(), anyString());
    }

    // ── markTransferred ──────────────────────────────────────────────────

    private PaymentRefund pendingRefund() {
        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setTransferStatus(RefundTransferStatus.PENDING_TRANSFER);
        return refund;
    }

    @Test
    void markTransferred_recordsReferenceNotifiesAndEmailsLearner() {
        PaymentRefund refund = pendingRefund();
        when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.of(refund));

        PaymentRefund saved = refundService.markTransferred(dispute, ADMIN_EMAIL, "  FT26111100123  ");

        assertThat(saved.getTransferStatus()).isEqualTo(RefundTransferStatus.TRANSFERRED);
        assertThat(saved.getTransferRef()).isEqualTo("FT26111100123");
        assertThat(saved.getTransferredBy()).isSameAs(admin);
        assertThat(saved.getTransferredAt()).isNotNull();
        verify(emailService).sendRefundTransferredEmail("hocvien@lms.local", "txn-100", "Unity co ban",
                new BigDecimal("1000000"), "FT26111100123");
        verify(notificationService).notify(eq(2L), eq("REFUND_TRANSFERRED"), anyString(), anyString(), anyString());
        verify(financeAuditService).record(eq(ADMIN_EMAIL), eq("REFUND_TRANSFERRED"), eq("payment"), eq("txn-100"), any(), any());
    }

    @Test
    void markTransferred_noApprovedRefund_blankRef_orAlreadyDone_areRejected() {
        assertThatThrownBy(() -> refundService.markTransferred(dispute, ADMIN_EMAIL, "REF"))
                .isInstanceOf(BusinessRuleViolationException.class); // chưa duyệt hoàn tiền

        PaymentRefund refund = pendingRefund();
        when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.of(refund));
        assertThatThrownBy(() -> refundService.markTransferred(dispute, ADMIN_EMAIL, " "))
                .isInstanceOf(BusinessRuleViolationException.class);

        refund.setTransferStatus(RefundTransferStatus.TRANSFERRED);
        assertThatThrownBy(() -> refundService.markTransferred(dispute, ADMIN_EMAIL, "REF2"))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(emailService, never()).sendRefundTransferredEmail(any(), any(), any(), any(), any());
    }

    @Test
    void instructorCharge_isNeverNegative_andMatchesEachMode() {
        assertThat(RefundService.instructorCharge(RefundResponsibility.STANDARD, payment)).isEqualByComparingTo("370000");
        assertThat(RefundService.instructorCharge(RefundResponsibility.PLATFORM_COMPENSATES, payment)).isEqualByComparingTo("0");
        assertThat(RefundService.instructorCharge(RefundResponsibility.INSTRUCTOR_FAULT, payment)).isEqualByComparingTo("1000000");
        payment.setInstructorEarning(null);
        assertThat(RefundService.instructorCharge(RefundResponsibility.STANDARD, payment)).isEqualByComparingTo("0");
    }

    @Test
    void policy_reasonCodeMustMatchTheResponsibilityMode_andNonStandardModesNeedACode() {
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.PLATFORM_COMPENSATES,
                RefundReasonCode.COPYRIGHT_VIOLATION, "ghi chú đầy đủ", null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.PLATFORM_COMPENSATES,
                null, "ghi chú đầy đủ", null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.STANDARD,
                RefundReasonCode.PROHIBITED_CONTENT, "ghi chú đầy đủ", null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(enrollmentService, never()).revokeForRefund(any(), any());
    }

    @Test
    void policy_instructorFault_needsEvidence_andTheInstructorMustHaveHadAChanceToRespond() {
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.INSTRUCTOR_FAULT,
                RefundReasonCode.COPYRIGHT_VIOLATION, "sao chép nguyên văn nội dung", "ngắn")).isInstanceOf(BusinessRuleViolationException.class);

        dispute.setInstructorDeadlineAt(java.time.LocalDateTime.now().plusHours(10)); // còn hạn, chưa phản hồi
        assertThatThrownBy(() -> refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.INSTRUCTOR_FAULT,
                RefundReasonCode.COPYRIGHT_VIOLATION, "sao chép nguyên văn nội dung", "so sánh với khóa gốc tại đường dẫn X"))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("chưa phản hồi");

        dispute.setInstructorDeadlineAt(java.time.LocalDateTime.now().minusHours(1)); // đã hết hạn phản hồi
        PaymentRefund refund = refundService.approve(dispute, ADMIN_EMAIL, RefundResponsibility.INSTRUCTOR_FAULT,
                RefundReasonCode.COPYRIGHT_VIOLATION, "sao chép nguyên văn nội dung", "so sánh với khóa gốc tại đường dẫn X");
        assertThat(refund.getReasonCode()).isEqualTo(RefundReasonCode.COPYRIGHT_VIOLATION);
        assertThat(refund.getEvidenceNote()).contains("khóa gốc");
        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("1000000");
    }
}
