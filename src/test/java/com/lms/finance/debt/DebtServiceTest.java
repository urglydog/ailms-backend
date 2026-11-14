package com.lms.finance.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.DebtPaymentStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorDebtPayment;
import com.lms.finance.entity.InstructorDebtState;
import com.lms.finance.repository.InstructorDebtPaymentRepository;
import com.lms.finance.repository.InstructorDebtStateRepository;
import com.lms.finance.repository.InstructorLedgerEntryRepository;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    private static final String EMAIL = "gv@lms.local";

    @Mock private LedgerService ledgerService;
    @Mock private InstructorLedgerEntryRepository ledgerRepository;
    @Mock private InstructorDebtStateRepository stateRepository;
    @Mock private InstructorDebtPaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private FinanceAuditService auditService;
    @Mock private SystemConfigService config;
    @Mock private DebtGuard guard;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private PayOS payOS;
    @Mock private PlatformTransactionManager txManager;

    @InjectMocks private DebtService service;

    private User instructor;
    private User admin;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "payosReturnUrl", "https://app.example.com/payments/callback");
        instructor = new User();
        instructor.setId(7L);
        instructor.setEmail(EMAIL);
        instructor.setRole(Role.INSTRUCTOR);
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@lms.local");
        admin.setRole(Role.ADMIN);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(config.getInt(eq("DEBT_MIN_REPAY"), anyInt())).thenReturn(10_000);
        lenient().when(guard.restrictDays()).thenReturn(30);
        lenient().when(guard.suspendDays()).thenReturn(90);
        lenient().when(paymentRepository.save(any(InstructorDebtPayment.class))).thenAnswer(inv -> {
            InstructorDebtPayment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(31L);
            }
            return p;
        });
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private void debt(long amount) {
        when(ledgerService.getBalance(7L)).thenReturn(new LedgerService.Balance(
                BigDecimal.valueOf(-amount), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private InstructorDebtPayment pending(long amount, long orderCode) {
        InstructorDebtPayment p = new InstructorDebtPayment();
        p.setId(31L);
        p.setInstructor(instructor);
        p.setAmount(amount);
        p.setOrderCode(orderCode);
        p.setStatus(DebtPaymentStatus.PENDING);
        return p;
    }

    // ── trả nợ ───────────────────────────────────────────────────────────

    @Test
    void createRepayment_partialAmount_createsPayosLinkInTheReservedOrderCodeRange() {
        debt(100_000);
        CreatePaymentLinkResponse link = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);
        when(link.getCheckoutUrl()).thenReturn("https://pay.example/checkout");
        when(link.getQrCode()).thenReturn("000201...");
        when(link.getPaymentLinkId()).thenReturn("pl-1");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(link);

        DebtDto.RepaymentRes res = service.createRepayment(EMAIL, 40_000);

        ArgumentCaptor<CreatePaymentLinkRequest> req = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(payOS.paymentRequests()).create(req.capture());
        assertThat(req.getValue().getAmount()).isEqualTo(40_000L);
        assertThat(req.getValue().getOrderCode()).isGreaterThanOrEqualTo(DebtService.ORDER_CODE_BASE);
        assertThat(req.getValue().getReturnUrl()).isEqualTo("https://app.example.com/instructor/earnings?debt=success");
        assertThat(res.amount()).isEqualTo(40_000L);
        assertThat(res.status()).isEqualTo(DebtPaymentStatus.PENDING);
        assertThat(res.qrCode()).isEqualTo("000201...");
    }

    @Test
    void createRepayment_rejectsNoDebt_overpaying_andTinyAmountsExceptPayingOff() {
        when(ledgerService.getBalance(7L)).thenReturn(new LedgerService.Balance(
                new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000")));
        assertThatThrownBy(() -> service.createRepayment(EMAIL, 10_000)).isInstanceOf(BusinessRuleViolationException.class);

        debt(100_000);
        assertThatThrownBy(() -> service.createRepayment(EMAIL, 100_001)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.createRepayment(EMAIL, 5_000)).isInstanceOf(BusinessRuleViolationException.class);
        verify(payOS.paymentRequests(), never()).create(any(CreatePaymentLinkRequest.class));
    }

    @Test
    void createRepayment_smallRemainingDebt_canBePaidOffEvenBelowTheMinimum() {
        debt(4_000);
        CreatePaymentLinkResponse link = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(link);

        assertThat(service.createRepayment(EMAIL, 4_000).amount()).isEqualTo(4_000L);
    }

    @Test
    void webhook_foreignOrderCodes_areNotHandled_andNonSuccessCodesDoNotSettle() {
        assertThat(service.handleWebhook(123_456L, 10_000L, "x", "00")).isFalse();
        verify(paymentRepository, never()).findByOrderCode(anyLong());

        long code = DebtService.ORDER_CODE_BASE + 5;
        when(paymentRepository.findByOrderCode(code)).thenReturn(Optional.of(pending(40_000, code)));
        assertThat(service.handleWebhook(code, 40_000L, "pl", "01")).isTrue();
        verify(ledgerService, never()).recordDebtRepayment(any(), any(), anyLong());
    }

    @Test
    void webhook_success_recordsTheLedgerOnce_notifiesAndKeepsRemainingDebt() {
        long code = DebtService.ORDER_CODE_BASE + 5;
        InstructorDebtPayment p = pending(40_000, code);
        when(paymentRepository.findByOrderCode(code)).thenReturn(Optional.of(p));
        debt(60_000);

        assertThat(service.handleWebhook(code, 40_000L, "pl-9", "00")).isTrue();
        assertThat(service.handleWebhook(code, 40_000L, "pl-9", "00")).isTrue(); // PayOS gửi lại

        assertThat(p.getStatus()).isEqualTo(DebtPaymentStatus.PAID);
        verify(ledgerService, org.mockito.Mockito.times(1)).recordDebtRepayment(instructor, new BigDecimal("40000"), 31L);
        verify(notificationService).notify(eq(7L), eq("DEBT_REPAID"), anyString(), org.mockito.ArgumentMatchers.contains("60000"), anyString());
    }

    @Test
    void webhook_amountMismatch_isNotBooked_andAdminsAreAlerted() {
        long code = DebtService.ORDER_CODE_BASE + 5;
        InstructorDebtPayment p = pending(40_000, code);
        when(paymentRepository.findByOrderCode(code)).thenReturn(Optional.of(p));

        service.handleWebhook(code, 39_000L, "pl", "00");

        assertThat(p.getStatus()).isEqualTo(DebtPaymentStatus.PENDING);
        verify(ledgerService, never()).recordDebtRepayment(any(), any(), anyLong());
        verify(notificationService).notify(eq(1L), eq("DEBT_AMOUNT_MISMATCH"), anyString(), anyString(), anyString());
    }

    // ── bậc nợ ───────────────────────────────────────────────────────────

    @Test
    void syncState_newDebt_createsStateAndNotifiesOnce_noDebt_removesIt() {
        when(stateRepository.findById(7L)).thenReturn(Optional.empty());
        when(stateRepository.save(any(InstructorDebtState.class))).thenAnswer(inv -> inv.getArgument(0));

        InstructorDebtState created = service.syncState(7L, 50_000, LocalDateTime.now());

        assertThat(created.getLastLevel()).isEqualTo(1);
        verify(notificationService).notify(eq(7L), eq("DEBT_CREATED"), anyString(), anyString(), anyString());

        when(stateRepository.findById(7L)).thenReturn(Optional.of(created));
        assertThat(service.syncState(7L, 0, LocalDateTime.now())).isNull();
        verify(stateRepository).delete(created);
    }

    @Test
    void syncState_levelUp_notifiesOnlyWhenTheLevelIncreases() {
        InstructorDebtState s = new InstructorDebtState();
        s.setInstructorId(7L);
        s.setDebtSince(LocalDateTime.now().minusDays(31));
        s.setLastLevel(1);
        when(stateRepository.findById(7L)).thenReturn(Optional.of(s));
        when(guard.levelFor(any(), any())).thenReturn(2);

        service.syncState(7L, 50_000, LocalDateTime.now());
        service.syncState(7L, 50_000, LocalDateTime.now());

        assertThat(s.getLastLevel()).isEqualTo(2);
        verify(notificationService, org.mockito.Mockito.times(1)).notify(eq(7L), eq("DEBT_LEVEL_UP"), anyString(), anyString(), anyString());
    }

    // ── admin xóa nợ ─────────────────────────────────────────────────────

    @Test
    void writeOff_needsReason_andCreditsExactlyTheDebt() {
        debt(80_000);
        assertThatThrownBy(() -> service.writeOff("admin@lms.local", 7L, " ")).isInstanceOf(BusinessRuleViolationException.class);

        service.writeOff("admin@lms.local", 7L, "Giảng viên ngừng hoạt động");

        verify(ledgerService).recordAdjustment(eq(instructor), eq(new BigDecimal("80000")), org.mockito.ArgumentMatchers.contains("Xóa nợ"), eq(1L));
        verify(notificationService).notify(eq(7L), eq("DEBT_WRITTEN_OFF"), anyString(), anyString(), anyString());
    }

    @Test
    void originOf_keepsSchemeAndHostOnly() {
        assertThat(DebtService.originOf("https://app.example.com/payments/callback")).isEqualTo("https://app.example.com");
        assertThat(DebtService.originOf("http://localhost:3000/payments/callback")).isEqualTo("http://localhost:3000");
    }
}
