package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.PayoutStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.dto.PayoutDto;
import com.lms.finance.entity.InstructorPayout;
import com.lms.finance.entity.InstructorPayoutAccount;
import com.lms.finance.repository.InstructorPayoutAccountRepository;
import com.lms.finance.repository.InstructorPayoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    private static final String EMAIL = "gv@lms.local";
    private static final String ADMIN_EMAIL = "admin@lms.local";

    @Mock private InstructorPayoutRepository payoutRepository;
    @Mock private InstructorPayoutAccountRepository accountRepository;
    @Mock private UserRepository userRepository;
    @Mock private LedgerService ledgerService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private FinanceConfirmationService confirmations;
    @Mock private FinanceAuditService auditService;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private PayoutCrypto crypto;
    @Mock private FinanceRateLimit rateLimit;
    @Mock private PayoutAccountService accountService;
    @Mock private com.lms.finance.payos.PayosOrderGuard payosOrderGuard;

    @InjectMocks
    private PayoutService service;

    private User instructor;
    private User admin;
    private InstructorPayoutAccount account;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(7L);
        instructor.setEmail(EMAIL);
        instructor.setFullName("Nguyễn Chí Thiện");
        instructor.setRole(Role.INSTRUCTOR);

        admin = new User();
        admin.setId(1L);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRole(Role.ADMIN);

        account = new InstructorPayoutAccount();
        account.setId(55L);
        account.setUser(instructor);
        account.setStatus(PayoutAccountStatus.ACTIVE);
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumberEnc("v1:enc");
        account.setAccountLast4("6789");
        account.setHolderName("NGUYEN CHI THIEN");
        account.setUsableFrom(LocalDateTime.now().minusDays(10));

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(instructor));
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.of(account));
        lenient().when(systemConfigService.getInt("PAYOUT_MIN_AMOUNT", 100_000)).thenReturn(100_000);
        lenient().when(systemConfigService.getString("PAYOUT_DAYS_OF_MONTH", "5,20")).thenReturn("5,20");
        lenient().when(ledgerService.getBalance(7L)).thenReturn(new LedgerService.Balance(
                new BigDecimal("500000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000")));
        lenient().when(payoutRepository.save(any(InstructorPayout.class))).thenAnswer(inv -> {
            InstructorPayout p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(90L);
            }
            return p;
        });
        lenient().when(confirmations.issue(anyString(), any(), any())).thenReturn("tok");
    }

    private InstructorPayout payout(PayoutStatus status) {
        InstructorPayout p = new InstructorPayout();
        p.setId(90L);
        p.setCode("PO-202611-000001");
        p.setInstructor(instructor);
        p.setAmount(new BigDecimal("200000"));
        p.setStatus(status);
        p.setBankCode("VCB");
        p.setBankName("Vietcombank");
        p.setAccountNumberEnc("v1:enc");
        p.setAccountLast4("6789");
        p.setHolderName("NGUYEN CHI THIEN");
        lenient().when(payoutRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(p));
        lenient().when(payoutRepository.findById(90L)).thenReturn(Optional.of(p));
        return p;
    }

    // ── request ─────────────────────────────────────────────────────────

    @Test
    void request_valid_reservesMoneyAudits_andSendsRequiredConfirmationEmail() {
        PayoutDto.PayoutRes res = service.request(EMAIL, new BigDecimal("200000"), "key-1");

        assertThat(res.status()).isEqualTo(PayoutStatus.PENDING_EMAIL);
        assertThat(res.amount()).isEqualByComparingTo("200000");
        assertThat(res.accountMask()).isEqualTo("•••• 6789");
        assertThat(res.code()).matches("PO-\\d{6}-\\d{6}");
        verify(ledgerService).recordPayoutReserve(eq(instructor), eq(new BigDecimal("200000")), eq(90L), anyString());
        verify(emailService).sendPayoutRequestConfirmEmail(eq(EMAIL), anyString(), eq(new BigDecimal("200000")), eq("tok"));
        verify(auditService).record(eq(EMAIL), eq("PAYOUT_REQUESTED"), eq("payout"), anyString(), any(), any());
        verify(rateLimit).check("payout-request", 7L, 5);
        ArgumentCaptor<InstructorPayout> captor = ArgumentCaptor.forClass(InstructorPayout.class);
        verify(payoutRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountNumberEnc()).isEqualTo("v1:enc"); // snapshot tài khoản
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void request_lockedInstructorRowIsUsedAsMutex() {
        service.request(EMAIL, new BigDecimal("200000"), null);

        verify(userRepository).findByIdForUpdate(7L);
    }

    @Test
    void request_sameIdempotencyKeyTwice_returnsTheFirstRequestWithoutReservingAgain() {
        InstructorPayout existing = payout(PayoutStatus.PENDING_EMAIL);
        when(payoutRepository.findByInstructor_IdAndIdempotencyKey(7L, "dup")).thenReturn(Optional.of(existing));

        PayoutDto.PayoutRes res = service.request(EMAIL, new BigDecimal("200000"), "dup");

        assertThat(res.id()).isEqualTo(90L);
        verify(ledgerService, never()).recordPayoutReserve(any(), any(), any(), any());
        verify(emailService, never()).sendPayoutRequestConfirmEmail(any(), any(), any(), any());
        verify(payoutRepository, never()).save(any(InstructorPayout.class));
    }

    @Test
    void request_amountBelowMinimum_aboveWithdrawable_orFractional_isRejected() {
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("99999"), null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("500001"), null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000.5"), null)).isInstanceOf(InvalidRequestException.class);

        verify(ledgerService, never()).recordPayoutReserve(any(), any(), any(), any());
    }

    @Test
    void request_exactlyTheWithdrawableBalanceAndMinimum_isAccepted() {
        service.request(EMAIL, new BigDecimal("500000.00"), null);
        service.request(EMAIL, new BigDecimal("100000"), null);

        verify(ledgerService, times(2)).recordPayoutReserve(any(), any(), any(), any());
    }

    @Test
    void request_withoutActiveAccount_withNewlyChangedAccount_orOpenPayout_isRejected() {
        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);

        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.of(account));
        account.setUsableFrom(LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);

        account.setUsableFrom(LocalDateTime.now().minusDays(1));
        when(payoutRepository.existsByInstructor_IdAndStatusIn(eq(7L), any())).thenReturn(true);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);

        verify(payoutRepository, never()).save(any(InstructorPayout.class));
    }

    @Test
    void request_restrictedAccount_orNotAnInstructor_orEncryptionNotConfigured_isRejected() {
        instructor.setIsSpamFlagged(true);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);
        instructor.setIsSpamFlagged(false);
        instructor.setIsActive(false);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);
        instructor.setIsActive(true);
        instructor.setRole(Role.STUDENT);
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(AccessDeniedDomainException.class);

        instructor.setRole(Role.INSTRUCTOR);
        doThrow(new BusinessRuleViolationException("PAYOUT_NOT_CONFIGURED", "x")).when(crypto).requireConfigured();
        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(BusinessRuleViolationException.class);

        verify(ledgerService, never()).recordPayoutReserve(any(), any(), any(), any());
    }

    @Test
    void request_whenConfirmationEmailCannotBeSent_failsSoReservationRollsBackWithTheTransaction() {
        doThrow(new RuntimeException("smtp down")).when(emailService).sendPayoutRequestConfirmEmail(any(), any(), any(), any());

        assertThatThrownBy(() -> service.request(EMAIL, new BigDecimal("200000"), null)).isInstanceOf(RuntimeException.class);
    }

    // ── confirm ─────────────────────────────────────────────────────────

    @Test
    void confirmByEmail_movesToRequested_schedulesNextBatch_notifiesAdminAndInstructor() {
        InstructorPayout p = payout(PayoutStatus.PENDING_EMAIL);

        PayoutDto.PayoutRes res = service.confirmByEmail(7L, 90L);

        assertThat(res.status()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(p.getEmailConfirmedAt()).isNotNull();
        assertThat(p.getScheduledFor()).isAfter(LocalDate.now());
        verify(emailService).sendPayoutRequestedEmail(eq(EMAIL), any(), eq("PO-202611-000001"), eq(new BigDecimal("200000")), any());
        verify(notificationService).notify(eq(1L), eq("PAYOUT_REQUESTED"), anyString(), anyString(), anyString());
    }

    @Test
    void confirmByEmail_wrongOwner_orWrongState_isRejected() {
        InstructorPayout p = payout(PayoutStatus.PENDING_EMAIL);
        assertThatThrownBy(() -> service.confirmByEmail(999L, 90L)).isInstanceOf(AccessDeniedDomainException.class);

        p.setStatus(PayoutStatus.CANCELLED);
        assertThatThrownBy(() -> service.confirmByEmail(7L, 90L)).isInstanceOf(BusinessRuleViolationException.class);
        p.setStatus(PayoutStatus.REQUESTED);
        assertThatThrownBy(() -> service.confirmByEmail(7L, 90L)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirmToken_dispatchesByType_andRejectsTokensIssuedToSomeoneElse() {
        payout(PayoutStatus.PENDING_EMAIL);
        when(confirmations.consume("ok")).thenReturn(Optional.of(new FinanceConfirmationService.Confirmation("PAYOUT", 90L, 7L)));
        assertThat(service.confirmToken(EMAIL, "ok").type()).isEqualTo("PAYOUT");

        when(confirmations.consume("acc")).thenReturn(Optional.of(new FinanceConfirmationService.Confirmation("ACCOUNT", 55L, 7L)));
        service.confirmToken(EMAIL, "acc");
        verify(accountService).confirm(7L, 55L);

        when(confirmations.consume("other")).thenReturn(Optional.of(new FinanceConfirmationService.Confirmation("PAYOUT", 90L, 999L)));
        assertThatThrownBy(() -> service.confirmToken(EMAIL, "other")).isInstanceOf(InvalidRequestException.class);
        when(confirmations.consume("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmToken(EMAIL, "bad")).isInstanceOf(InvalidRequestException.class);
        when(confirmations.consume("weird")).thenReturn(Optional.of(new FinanceConfirmationService.Confirmation("LOL", 1L, 7L)));
        assertThatThrownBy(() -> service.confirmToken(EMAIL, "weird")).isInstanceOf(InvalidRequestException.class);
    }

    // ── cancel / release ────────────────────────────────────────────────

    @Test
    void cancel_beforeApproval_releasesTheReservedMoney() {
        InstructorPayout p = payout(PayoutStatus.REQUESTED);

        PayoutDto.PayoutRes res = service.cancel(EMAIL, 90L);

        assertThat(res.status()).isEqualTo(PayoutStatus.CANCELLED);
        assertThat(p.getStatus()).isEqualTo(PayoutStatus.CANCELLED);
        verify(ledgerService).recordPayoutRelease(eq(instructor), eq(new BigDecimal("200000")), eq(90L), anyString());
    }

    @Test
    void cancel_afterApproval_byAnotherInstructor_orWhenPaid_isRejected() {
        InstructorPayout p = payout(PayoutStatus.APPROVED);
        assertThatThrownBy(() -> service.cancel(EMAIL, 90L)).isInstanceOf(BusinessRuleViolationException.class);

        p.setStatus(PayoutStatus.REQUESTED);
        assertThatThrownBy(() -> service.cancel("khac@lms.local", 90L)).isInstanceOf(AccessDeniedDomainException.class);

        verify(ledgerService, never()).recordPayoutRelease(any(), any(), any(), any());
    }

    // ── admin ───────────────────────────────────────────────────────────

    @Test
    void approve_onlyFromRequested() {
        InstructorPayout p = payout(PayoutStatus.REQUESTED);

        PayoutDto.AdminPayoutRes res = service.approve(ADMIN_EMAIL, 90L);

        assertThat(res.status()).isEqualTo(PayoutStatus.APPROVED);
        assertThat(p.getApprovedBy()).isSameAs(admin);
        assertThat(p.getApprovedAt()).isNotNull();
        verify(auditService).record(eq(ADMIN_EMAIL), eq("PAYOUT_APPROVED"), eq("payout"), anyString(), any(), any());
        verify(notificationService).notify(eq(7L), eq("PAYOUT_APPROVED"), anyString(), anyString(), eq("/instructor/earnings"));

        for (PayoutStatus bad : new PayoutStatus[]{PayoutStatus.PENDING_EMAIL, PayoutStatus.APPROVED, PayoutStatus.PAID,
                PayoutStatus.REJECTED, PayoutStatus.CANCELLED, PayoutStatus.FAILED}) {
            p.setStatus(bad);
            assertThatThrownBy(() -> service.approve(ADMIN_EMAIL, 90L)).isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Test
    void markPaid_requiresApprovedStateAndTransferReference_thenEmailsAndNotifies() {
        InstructorPayout p = payout(PayoutStatus.APPROVED);
        assertThatThrownBy(() -> service.markPaid(ADMIN_EMAIL, 90L, "  ", null)).isInstanceOf(InvalidRequestException.class);

        PayoutDto.AdminPayoutRes res = service.markPaid(ADMIN_EMAIL, 90L, " FT123 ", " http://proof ");

        assertThat(res.status()).isEqualTo(PayoutStatus.PAID);
        assertThat(p.getTransferRef()).isEqualTo("FT123");
        assertThat(p.getProofUrl()).isEqualTo("http://proof");
        assertThat(p.getPaidBy()).isSameAs(admin);
        verify(emailService).sendPayoutPaidEmail(eq(EMAIL), any(), eq("PO-202611-000001"), eq(new BigDecimal("200000")), anyString(), any(), eq("FT123"));
        verify(notificationService).notify(eq(7L), eq("PAYOUT_PAID"), eq("Rút tiền thành công"), org.mockito.ArgumentMatchers.contains("FT123"), anyString());
        verify(ledgerService, never()).recordPayoutRelease(any(), any(), any(), any()); // đã chi thật: KHÔNG trả lại số dư

        p.setStatus(PayoutStatus.REQUESTED);
        assertThatThrownBy(() -> service.markPaid(ADMIN_EMAIL, 90L, "FT9", null)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reject_andMarkFailed_releaseTheMoneyAndTellTheInstructorWhy() {
        payout(PayoutStatus.REQUESTED);
        service.reject(ADMIN_EMAIL, 90L, " Sai thông tin ");
        verify(ledgerService).recordPayoutRelease(eq(instructor), eq(new BigDecimal("200000")), eq(90L), anyString());
        verify(emailService).sendPayoutRejectedEmail(eq(EMAIL), any(), eq("PO-202611-000001"), eq(new BigDecimal("200000")), eq("Sai thông tin"), eq(false));

        InstructorPayout failing = payout(PayoutStatus.APPROVED);
        service.markFailed(ADMIN_EMAIL, 90L, "Ngân hàng từ chối");
        assertThat(failing.getStatus()).isEqualTo(PayoutStatus.FAILED);
        verify(emailService).sendPayoutRejectedEmail(eq(EMAIL), any(), eq("PO-202611-000001"), eq(new BigDecimal("200000")), eq("Ngân hàng từ chối"), eq(true));
        verify(notificationService, times(2)).notify(eq(7L), eq("PAYOUT_REJECTED"), anyString(), anyString(), anyString());
    }

    @Test
    void reject_requiresReason_andValidState() {
        InstructorPayout p = payout(PayoutStatus.REQUESTED);
        assertThatThrownBy(() -> service.reject(ADMIN_EMAIL, 90L, " ")).isInstanceOf(InvalidRequestException.class);

        p.setStatus(PayoutStatus.PAID);
        assertThatThrownBy(() -> service.reject(ADMIN_EMAIL, 90L, "x")).isInstanceOf(BusinessRuleViolationException.class);
        p.setStatus(PayoutStatus.REQUESTED);
        assertThatThrownBy(() -> service.markFailed(ADMIN_EMAIL, 90L, "x")).isInstanceOf(BusinessRuleViolationException.class);
        verify(ledgerService, never()).recordPayoutRelease(any(), any(), any(), any());
    }

    @Test
    void releaseIsOnlyEverRecordedOncePerTransition_evenIfAdminDoubleClicks() {
        InstructorPayout p = payout(PayoutStatus.REQUESTED);
        service.reject(ADMIN_EMAIL, 90L, "x");
        assertThat(p.getStatus()).isEqualTo(PayoutStatus.REJECTED);

        assertThatThrownBy(() -> service.reject(ADMIN_EMAIL, 90L, "x")).isInstanceOf(BusinessRuleViolationException.class);
        verify(ledgerService, times(1)).recordPayoutRelease(any(), any(), any(), any());
    }

    @Test
    void revealAccount_decryptsForAdminAndAuditsEveryView_withoutLoggingTheNumber() {
        payout(PayoutStatus.APPROVED);
        when(accountService.decryptForAdmin("v1:enc")).thenReturn("0123456789");

        PayoutDto.AccountRevealRes res = service.revealAccount(ADMIN_EMAIL, 90L);

        assertThat(res.accountNumber()).isEqualTo("0123456789");
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq(ADMIN_EMAIL), eq("PAYOUT_ACCOUNT_VIEWED"), eq("payout"), eq("PO-202611-000001"), any(), after.capture());
        assertThat(String.valueOf(after.getValue())).doesNotContain("0123456789");
    }

    @Test
    void exportApprovedCsv_escapesFormulaInjection_andAuditsWithoutAccountNumbers() {
        InstructorPayout p = payout(PayoutStatus.APPROVED);
        instructor.setFullName("=HYPERLINK(\"http://evil\")");
        p.setHolderName("+CMD|' /C calc'!A0");
        when(payoutRepository.findByStatusOrderByCreatedAtDesc(PayoutStatus.APPROVED)).thenReturn(List.of(p));
        when(accountService.decryptForAdmin("v1:enc")).thenReturn("0123456789");

        String csv = service.exportApprovedCsv(ADMIN_EMAIL);

        assertThat(csv).startsWith("Mã yêu cầu,Giảng viên,Ngân hàng,Số tài khoản,Chủ tài khoản,Số tiền,Nội dung\n");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"").contains("\"'+CMD|' /C calc'!A0\"");
        assertThat(csv).contains("\"0123456789\"").contains(",200000,");
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq(ADMIN_EMAIL), eq("PAYOUT_EXPORT_CSV"), eq("payout"), any(), any(), after.capture());
        assertThat(String.valueOf(after.getValue())).doesNotContain("0123456789");
    }

    @Test
    void csvCell_prefixesDangerousLeadingCharactersAndDoublesQuotes() {
        assertThat(PayoutService.csv("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(PayoutService.csv("-5")).isEqualTo("\"'-5\"");
        assertThat(PayoutService.csv("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(PayoutService.csv("an \"toàn\"")).isEqualTo("\"an \"\"toàn\"\"\"");
        assertThat(PayoutService.csv(null)).isEqualTo("\"\"");
    }

    // ── job ─────────────────────────────────────────────────────────────

    @Test
    void expireUnconfirmed_cancelsOnlyStillPendingRequestsAndReleasesTheirMoney() {
        LocalDateTime now = LocalDateTime.now();
        InstructorPayout stale = payout(PayoutStatus.PENDING_EMAIL);
        when(payoutRepository.findByStatusAndCreatedAtBefore(eq(PayoutStatus.PENDING_EMAIL), any())).thenReturn(List.of(stale));

        int cancelled = service.expireUnconfirmed(now);

        assertThat(cancelled).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(PayoutStatus.CANCELLED);
        verify(ledgerService).recordPayoutRelease(eq(instructor), eq(new BigDecimal("200000")), eq(90L), anyString());
    }

    @Test
    void expireUnconfirmed_requestConfirmedMeanwhile_isLeftAlone() {
        InstructorPayout confirmedMeanwhile = payout(PayoutStatus.REQUESTED);
        when(payoutRepository.findByStatusAndCreatedAtBefore(eq(PayoutStatus.PENDING_EMAIL), any())).thenReturn(List.of(confirmedMeanwhile));

        assertThat(service.expireUnconfirmed(LocalDateTime.now())).isZero();
        verify(ledgerService, never()).recordPayoutRelease(any(), any(), any(), any());
    }

    @Test
    void summary_combinesBalanceAccountAndSchedule() {
        when(payoutRepository.sumPaid(7L)).thenReturn(new BigDecimal("300000"));
        when(accountService.getCurrent(EMAIL)).thenReturn(null);
        when(crypto.isConfigured()).thenReturn(true);

        PayoutDto.SummaryRes s = service.summary(EMAIL);

        assertThat(s.withdrawable()).isEqualByComparingTo("500000");
        assertThat(s.paidOut()).isEqualByComparingTo("300000");
        assertThat(s.minPayoutAmount()).isEqualByComparingTo("100000");
        assertThat(s.nextPayoutDate()).isAfter(LocalDate.now());
        assertThat(s.cryptoConfigured()).isTrue();
        assertThat(s.hasOpenPayout()).isFalse();
    }

    @Test
    void manualActions_areBlockedAtTheServiceLayerWhileAPayosOrderIsUnsettled() {
        payout(PayoutStatus.APPROVED);
        org.mockito.Mockito.doThrow(new BusinessRuleViolationException("Đang có lệnh chi PayOS")).when(payosOrderGuard)
                .assertNoUnsettled(com.lms.common.enums.PayosOrderKind.INSTRUCTOR_PAYOUT, 90L);

        assertThatThrownBy(() -> service.markPaid(ADMIN_EMAIL, 90L, "FT1", null)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.reject(ADMIN_EMAIL, 90L, "lý do")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.markFailed(ADMIN_EMAIL, 90L, "lý do")).isInstanceOf(BusinessRuleViolationException.class);

        verify(payoutRepository, org.mockito.Mockito.never()).save(any());
    }
}
