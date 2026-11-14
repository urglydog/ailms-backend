package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.AppealStatus;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.debt.DebtService;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundAppealServiceTest {

    private static final String INSTRUCTOR_EMAIL = "gv@lms.local";
    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private PaymentDisputeRepository disputeRepository;
    @Mock private PaymentRefundRepository refundRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private FinanceAuditService auditService;
    @Mock private LedgerService ledgerService;
    @Mock private DebtService debtService;
    @Mock private SystemConfigService config;
    @InjectMocks private RefundAppealService service;

    private User instructor;
    private User admin;
    private Payment payment;
    private PaymentDispute dispute;
    private PaymentRefund refund;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(3L);
        instructor.setEmail(INSTRUCTOR_EMAIL);
        instructor.setFullName("GV A");
        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);
        Course course = new Course();
        course.setId(10L);
        course.setInstructor(instructor);
        payment = new Payment();
        payment.setId(100L);
        payment.setTxnRef("txn-100");
        payment.setCourse(course);
        payment.setAmount(new BigDecimal("200000"));
        payment.setInstructorEarning(new BigDecimal("74000"));
        dispute = new PaymentDispute();
        dispute.setId(500L);
        dispute.setPayment(payment);
        refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setAmount(new BigDecimal("200000"));
        refund.setResponsibility(RefundResponsibility.INSTRUCTOR_FAULT);
        refund.setInstructorCharge(new BigDecimal("200000"));
        refund.setApprovedAt(LocalDateTime.now().minusDays(1));
        lenient().when(disputeRepository.findById(500L)).thenReturn(Optional.of(dispute));
        lenient().when(refundRepository.findByPayment_Id(100L)).thenReturn(Optional.of(refund));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(config.getInt("DISPUTE_APPEAL_DAYS", 7)).thenReturn(7);
    }

    private static final String REASON = "Nội dung khóa học do chính tôi biên soạn, có bản gốc đính kèm";

    @Test
    void appeal_valid_opensItOnce_notifiesAdmins() {
        service.appeal(INSTRUCTOR_EMAIL, 500L, REASON);

        assertThat(refund.getAppealStatus()).isEqualTo(AppealStatus.OPEN);
        assertThat(refund.getAppealReason()).isEqualTo(REASON);
        verify(notificationService).notify(eq(1L), eq("REFUND_APPEAL"), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.appeal(INSTRUCTOR_EMAIL, 500L, REASON)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void appeal_rejectedForOtherInstructors_otherModes_shortReasons_andAfterTheDeadline() {
        assertThatThrownBy(() -> service.appeal("khac@lms.local", 500L, REASON)).isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.appeal(INSTRUCTOR_EMAIL, 500L, "ngắn")).isInstanceOf(BusinessRuleViolationException.class);

        refund.setApprovedAt(LocalDateTime.now().minusDays(8));
        assertThatThrownBy(() -> service.appeal(INSTRUCTOR_EMAIL, 500L, REASON)).isInstanceOf(BusinessRuleViolationException.class);

        refund.setApprovedAt(LocalDateTime.now().minusDays(1));
        refund.setResponsibility(RefundResponsibility.STANDARD);
        assertThatThrownBy(() -> service.appeal(INSTRUCTOR_EMAIL, 500L, REASON)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(refund.getAppealStatus()).isEqualTo(AppealStatus.NONE);
    }

    @Test
    void decide_reduceToStandard_givesBackOnlyTheExcessPenalty_andRefreshesDebt() {
        refund.setAppealStatus(AppealStatus.OPEN);

        service.decide(ADMIN_EMAIL, 500L, DisputeDto.AppealDecision.REDUCE_TO_STANDARD, "Bằng chứng chưa đủ");

        assertThat(refund.getAppealStatus()).isEqualTo(AppealStatus.REDUCED);
        assertThat(refund.getResponsibility()).isEqualTo(RefundResponsibility.STANDARD);
        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("74000");
        assertThat(refund.getPlatformShare()).isEqualByComparingTo("126000");
        verify(ledgerService).recordAdjustment(eq(instructor), eq(new BigDecimal("126000")), anyString(), eq(1L));
        verify(debtService).refreshState(3L);
        verify(notificationService).notify(eq(3L), eq("REFUND_APPEAL_RESULT"), anyString(), anyString(), anyString());
    }

    @Test
    void decide_uphold_changesNothingFinancial_andOnlyAnOpenAppealCanBeDecided() {
        assertThatThrownBy(() -> service.decide(ADMIN_EMAIL, 500L, DisputeDto.AppealDecision.UPHOLD, "ok"))
                .isInstanceOf(BusinessRuleViolationException.class);

        refund.setAppealStatus(AppealStatus.OPEN);
        assertThatThrownBy(() -> service.decide(ADMIN_EMAIL, 500L, DisputeDto.AppealDecision.UPHOLD, " "))
                .isInstanceOf(BusinessRuleViolationException.class);

        service.decide(ADMIN_EMAIL, 500L, DisputeDto.AppealDecision.UPHOLD, "Giữ nguyên vì có bằng chứng sao chép");

        assertThat(refund.getAppealStatus()).isEqualTo(AppealStatus.UPHELD);
        assertThat(refund.getInstructorCharge()).isEqualByComparingTo("200000");
        verify(ledgerService, never()).recordAdjustment(any(), any(), any(), any());
    }
}
