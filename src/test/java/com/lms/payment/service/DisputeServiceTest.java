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
import com.lms.common.enums.DisputeCategory;
import com.lms.common.enums.DisputeEligibility;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.common.storage.StorageService;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.DisputeAttachmentRepository;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    private static final String LEARNER_EMAIL = "hocvien@lms.local";
    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private PaymentDisputeRepository disputeRepository;
    @Mock private DisputeAttachmentRepository attachmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private StorageService storageService;
    @Mock private RefundService refundService;
    @Mock private RefundPolicyService refundPolicyService;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private PaymentRefundRepository refundRepository;

    @InjectMocks
    private DisputeService disputeService;

    private User learner;
    private User instructor;
    private User admin;
    private Course course;
    private Payment payment;

    @BeforeEach
    void setUp() {
        learner = new User();
        learner.setId(2L);
        learner.setEmail(LEARNER_EMAIL);
        learner.setFullName("Hoc Vien");
        instructor = new User();
        instructor.setId(3L);
        instructor.setEmail("gv@lms.local");
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        course = new Course();
        course.setId(10L);
        course.setTitle("Unity co ban");
        course.setInstructor(instructor);

        payment = new Payment();
        payment.setId(100L);
        payment.setTxnRef("txn-100");
        payment.setUser(learner);
        payment.setCourse(course);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("1000000"));
        payment.setPaidAt(LocalDateTime.now().minusDays(3));

        ReflectionTestUtils.setField(disputeService, "maxEvidenceFiles", 5);
        ReflectionTestUtils.setField(disputeService, "maxEvidenceSizeMb", 20L);
        ReflectionTestUtils.setField(disputeService, "reopenWindowDays", 7);

        Enrollment enrollment = new Enrollment();
        enrollment.setProgressPct(new BigDecimal("10"));

        lenient().when(userRepository.findByEmail(LEARNER_EMAIL)).thenReturn(Optional.of(learner));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(paymentRepository.findByTxnRef("txn-100")).thenReturn(Optional.of(payment));
        lenient().when(disputeRepository.existsByPayment_Id(100L)).thenReturn(false);
        lenient().when(disputeRepository.save(any(PaymentDispute.class))).thenAnswer(inv -> {
            PaymentDispute d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(500L);
            }
            return d;
        });
        lenient().when(enrollmentRepository.findByUser_IdAndCourse_Id(2L, 10L)).thenReturn(Optional.of(enrollment));
        lenient().when(refundPolicyService.evaluate(any(), any(), any(), any())).thenReturn(DisputeEligibility.AUTO_ELIGIBLE);
        lenient().when(systemConfigService.getInt("DISPUTE_INSTRUCTOR_RESPONSE_HOURS", 48)).thenReturn(48);
    }

    private DisputeDto.FileReq fileReq(DisputeCategory category) {
        return new DisputeDto.FileReq("txn-100", "Không phù hợp", "mô tả", "VCB 0123", true, category, null, null, null, null);
    }

    // ── fileDispute ─────────────────────────────────────────────────────

    @Test
    void fileDispute_valid_recordsCategoryEligibilityProgressDeadlineAndNotifiesInstructor() {
        LocalDateTime before = LocalDateTime.now();

        disputeService.fileDispute(LEARNER_EMAIL, fileReq(DisputeCategory.CHANGED_MIND), null);

        ArgumentCaptor<PaymentDispute> captor = ArgumentCaptor.forClass(PaymentDispute.class);
        verify(disputeRepository).save(captor.capture());
        PaymentDispute d = captor.getValue();
        assertThat(d.getCategory()).isEqualTo(DisputeCategory.CHANGED_MIND);
        assertThat(d.getEligibility()).isEqualTo(DisputeEligibility.AUTO_ELIGIBLE);
        assertThat(d.getProgressPctAtFiling()).isEqualByComparingTo("10");
        assertThat(d.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(d.getInstructorDeadlineAt()).isBetween(before.plusHours(48), LocalDateTime.now().plusHours(48).plusSeconds(1));
        verify(notificationService).notify(eq(3L), eq("DISPUTE_FILED_INSTRUCTOR"), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(1L), eq("DISPUTE_FILED"), anyString(), anyString(), anyString());
    }

    private DisputeDto.FileReq structuredReq(String bank, String number, String holder, Boolean own) {
        return new DisputeDto.FileReq("txn-100", "Không phù hợp", "mô tả", null, true, DisputeCategory.CHANGED_MIND, bank, number, holder, own);
    }

    @Test
    void fileDispute_structuredRefundAccount_isValidatedNormalizedAndStored() {
        disputeService.fileDispute(LEARNER_EMAIL, structuredReq("tcb", "1907 0154 183018", "Nguyễn Chí Thiện", true), null);

        ArgumentCaptor<PaymentDispute> captor = ArgumentCaptor.forClass(PaymentDispute.class);
        verify(disputeRepository).save(captor.capture());
        PaymentDispute d = captor.getValue();
        assertThat(d.getRefundBankCode()).isEqualTo("TCB");
        assertThat(d.getRefundAccountNumber()).isEqualTo("19070154183018");
        assertThat(d.getRefundAccountHolder()).isEqualTo("NGUYEN CHI THIEN");
        assertThat(d.getRefundAccountConfirmed()).isTrue();
        assertThat(d.getRefundBankAccount()).isEqualTo("Techcombank 19070154183018 - NGUYEN CHI THIEN");
    }

    @Test
    void fileDispute_structuredRefundAccount_requiresOwnershipConfirmationValidBankAndNumber() {
        for (DisputeDto.FileReq bad : new DisputeDto.FileReq[] {
                structuredReq("TCB", "19070154183018", "Nguyen A", false),
                structuredReq("TCB", "19070154183018", "Nguyen A", null),
                structuredReq("XXX", "19070154183018", "Nguyen A", true),
                structuredReq("TCB", "12ab", "Nguyen A", true),
                structuredReq("TCB", "19070154183018", " ", true),
                structuredReq(null, "19070154183018", "Nguyen A", true)}) {
            assertThatThrownBy(() -> disputeService.fileDispute(LEARNER_EMAIL, bad, null))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
        verify(disputeRepository, never()).save(any(PaymentDispute.class));
    }

    @Test
    void fileDispute_oldClientWithoutCategory_defaultsToOther() {
        disputeService.fileDispute(LEARNER_EMAIL, fileReq(null), null);

        ArgumentCaptor<PaymentDispute> captor = ArgumentCaptor.forClass(PaymentDispute.class);
        verify(disputeRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(DisputeCategory.OTHER);
    }

    @Test
    void fileDispute_paymentAlreadyHasADispute_isRejected_soRefundCannotHappenTwice() {
        when(disputeRepository.existsByPayment_Id(100L)).thenReturn(true);

        assertThatThrownBy(() -> disputeService.fileDispute(LEARNER_EMAIL, fileReq(DisputeCategory.OTHER), null))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(disputeRepository, never()).save(any(PaymentDispute.class));
    }

    @Test
    void fileDispute_notPaidAnymore_zeroAmount_orSomeoneElsesPayment_areRejected() {
        payment.setStatus(PaymentStatus.REFUNDED);
        assertThatThrownBy(() -> disputeService.fileDispute(LEARNER_EMAIL, fileReq(DisputeCategory.OTHER), null))
                .isInstanceOf(BusinessRuleViolationException.class);

        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> disputeService.fileDispute(LEARNER_EMAIL, fileReq(DisputeCategory.OTHER), null))
                .isInstanceOf(BusinessRuleViolationException.class);

        payment.setAmount(new BigDecimal("1000000"));
        User other = new User();
        other.setId(99L);
        payment.setUser(other);
        assertThatThrownBy(() -> disputeService.fileDispute(LEARNER_EMAIL, fileReq(DisputeCategory.OTHER), null))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(disputeRepository, never()).save(any(PaymentDispute.class));
    }

    // ── resolve ─────────────────────────────────────────────────────────

    private PaymentDispute openDispute() {
        PaymentDispute d = new PaymentDispute();
        d.setId(500L);
        d.setPayment(payment);
        d.setFiledBy(learner);
        d.setReason("Không phù hợp");
        d.setStatus(DisputeStatus.OPEN);
        when(disputeRepository.findById(500L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void resolve_refunded_performsRealRefundWithChosenResponsibilityAndEmailsLearner() {
        PaymentDispute d = openDispute();

        DisputeDto.AdminRes res = disputeService.resolve(ADMIN_EMAIL, 500L,
                new DisputeDto.ResolveReq(DisputeStatus.RESOLVED_REFUNDED, "Đồng ý hoàn", RefundResponsibility.PLATFORM_COMPENSATES, null, null));

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED_REFUNDED);
        assertThat(d.getResolvedBy()).isSameAs(admin);
        assertThat(res.status()).isEqualTo(DisputeStatus.RESOLVED_REFUNDED);
        verify(refundService).approve(d, ADMIN_EMAIL, RefundResponsibility.PLATFORM_COMPENSATES, null, "Đồng ý hoàn", null);
        verify(emailService).sendDisputeResolutionEmail(eq(LEARNER_EMAIL), eq(true), eq("txn-100"), eq("Unity co ban"),
                eq(new BigDecimal("1000000")), anyString(), eq("Đồng ý hoàn"), any());
    }

    @Test
    void resolve_rejected_doesNotRefundAndTellsInstructorTheirEarningIsSafe() {
        PaymentDispute d = openDispute();

        disputeService.resolve(ADMIN_EMAIL, 500L,
                new DisputeDto.ResolveReq(DisputeStatus.RESOLVED_REJECTED, "Ngoài chính sách", null, null, null));

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RESOLVED_REJECTED);
        verify(refundService, never()).approve(any(), any(), any(), any(), any(), any());
        verify(notificationService).notify(eq(3L), eq("DISPUTE_REJECTED_INSTRUCTOR"), anyString(), anyString(), anyString());
    }

    @Test
    void resolve_statusThatIsNotAFinalDecision_isRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> disputeService.resolve(ADMIN_EMAIL, 500L,
                new DisputeDto.ResolveReq(DisputeStatus.OPEN, "x", null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> disputeService.resolve(ADMIN_EMAIL, 500L,
                new DisputeDto.ResolveReq(DisputeStatus.REOPENED, "x", null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(disputeRepository, never()).findById(any());
    }

    @Test
    void resolve_alreadyResolved_isRejected() {
        PaymentDispute d = openDispute();
        d.setStatus(DisputeStatus.RESOLVED_REJECTED);

        assertThatThrownBy(() -> disputeService.resolve(ADMIN_EMAIL, 500L,
                new DisputeDto.ResolveReq(DisputeStatus.RESOLVED_REFUNDED, "x", null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(refundService, never()).approve(any(), any(), any(), any(), any(), any());
    }

    @Test
    void markTransferred_delegatesToRefundService() {
        PaymentDispute d = openDispute();

        disputeService.markTransferred(ADMIN_EMAIL, 500L, new DisputeDto.MarkTransferredReq("FT123"));

        verify(refundService).markTransferred(d, ADMIN_EMAIL, "FT123");
    }
}
