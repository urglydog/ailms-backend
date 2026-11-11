package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.NameMatchStatus;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.NotificationService;
import com.lms.finance.dto.PayoutDto;
import com.lms.finance.entity.InstructorPayoutAccount;
import com.lms.finance.repository.InstructorPayoutAccountRepository;
import com.lms.instructor.repository.InstructorVerificationRepository;
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
class PayoutAccountServiceTest {

    private static final String EMAIL = "gv@lms.local";

    @Mock private InstructorPayoutAccountRepository accountRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstructorVerificationRepository verificationRepository;
    @Mock private PayoutCrypto crypto;
    @Mock private FinanceConfirmationService confirmations;
    @Mock private FinanceAuditService auditService;
    @Mock private EmailService emailService;
    @Mock private FinanceRateLimit rateLimit;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private PayoutAccountService service;

    private User instructor;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(7L);
        instructor.setEmail(EMAIL);
        instructor.setFullName("Nguyễn Chí Thiện");
        instructor.setRole(Role.INSTRUCTOR);

        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(instructor));
        lenient().when(verificationRepository.existsByUser_Id(7L)).thenReturn(true);
        lenient().when(crypto.encrypt("0123456789")).thenReturn("v1:enc");
        lenient().when(crypto.hash("0123456789")).thenReturn("hash-0123456789");
        lenient().when(accountRepository.save(any(InstructorPayoutAccount.class))).thenAnswer(inv -> {
            InstructorPayoutAccount a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(55L);
            }
            return a;
        });
        lenient().when(confirmations.issue(anyString(), any(), any())).thenReturn("token-abc");
    }

    private PayoutDto.LinkAccountReq req(String bank, String number, String holder) {
        return new PayoutDto.LinkAccountReq(bank, number, holder);
    }

    // ── link ────────────────────────────────────────────────────────────

    @Test
    void link_valid_storesEncryptedNumberOnly_masksIt_andSendsConfirmationEmail() {
        PayoutDto.AccountRes res = service.link(EMAIL, req("vcb", "0123 456 789", "Nguyễn Chí Thiện"));

        ArgumentCaptor<InstructorPayoutAccount> captor = ArgumentCaptor.forClass(InstructorPayoutAccount.class);
        verify(accountRepository).save(captor.capture());
        InstructorPayoutAccount a = captor.getValue();
        assertThat(a.getAccountNumberEnc()).isEqualTo("v1:enc");
        assertThat(a.getAccountLast4()).isEqualTo("6789");
        assertThat(a.getAccountHash()).isEqualTo("hash-0123456789");
        assertThat(a.getHolderName()).isEqualTo("NGUYEN CHI THIEN");
        assertThat(a.getBankCode()).isEqualTo("VCB");
        assertThat(a.getStatus()).isEqualTo(PayoutAccountStatus.PENDING_CONFIRM);
        assertThat(a.getNameMatchStatus()).isEqualTo(NameMatchStatus.MATCHED);
        assertThat(res.accountMask()).isEqualTo("•••• 6789").doesNotContain("0123");
        verify(emailService).sendPayoutAccountConfirmEmail(EMAIL, "Vietcombank", "6789", "token-abc");
        verify(rateLimit).check("account-link", 7L, 5);
    }

    @Test
    void link_neverWritesTheFullAccountNumberToTheAuditLog() {
        service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện"));

        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq(EMAIL), eq("PAYOUT_ACCOUNT_LINK_REQUESTED"), eq("payout_account"), eq("55"), any(), after.capture());
        assertThat(String.valueOf(after.getValue())).doesNotContain("0123456789");
    }

    @Test
    void link_holderNameDifferentFromVerifiedName_isFlaggedMismatch() {
        service.link(EMAIL, req("VCB", "0123456789", "TRAN VAN B"));

        ArgumentCaptor<InstructorPayoutAccount> captor = ArgumentCaptor.forClass(InstructorPayoutAccount.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getNameMatchStatus()).isEqualTo(NameMatchStatus.MISMATCH);
    }

    @Test
    void link_replacesAnyEarlierPendingAccount() {
        InstructorPayoutAccount oldPending = new InstructorPayoutAccount();
        oldPending.setId(40L);
        oldPending.setStatus(PayoutAccountStatus.PENDING_CONFIRM);
        when(accountRepository.findByUser_IdAndStatus(7L, PayoutAccountStatus.PENDING_CONFIRM)).thenReturn(List.of(oldPending));

        service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện"));

        assertThat(oldPending.getStatus()).isEqualTo(PayoutAccountStatus.REPLACED);
    }

    @Test
    void link_rejectsInvalidBankNumberAndHolder() {
        assertThatThrownBy(() -> service.link(EMAIL, req("KHONG-CO", "0123456789", "A B")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "12ab56", "A B")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "12345", "A B")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "0123456789", "123 !!!")))
                .isInstanceOf(InvalidRequestException.class);
        verify(accountRepository, never()).save(any(InstructorPayoutAccount.class));
    }

    @Test
    void link_withoutInstructorVerification_orNotInstructor_orEncryptionNotConfigured_isRejected() {
        when(verificationRepository.existsByUser_Id(7L)).thenReturn(false);
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện")))
                .isInstanceOf(BusinessRuleViolationException.class);

        instructor.setRole(Role.STUDENT);
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện")))
                .isInstanceOf(AccessDeniedDomainException.class);

        instructor.setRole(Role.INSTRUCTOR);
        // requireConfigured() chạy TRƯỚC kiểm tra xác minh nên không cần đặt lại stub xác minh ở bước này.
        doThrow(new BusinessRuleViolationException("PAYOUT_NOT_CONFIGURED", "chưa cấu hình")).when(crypto).requireConfigured();
        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện")))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(accountRepository, never()).save(any(InstructorPayoutAccount.class));
        verify(emailService, never()).sendPayoutAccountConfirmEmail(any(), any(), any(), any());
    }

    @Test
    void link_whenConfirmationEmailFails_propagatesSoTheTransactionRollsBack() {
        doThrow(new RuntimeException("smtp down")).when(emailService).sendPayoutAccountConfirmEmail(any(), any(), any(), any());

        assertThatThrownBy(() -> service.link(EMAIL, req("VCB", "0123456789", "Nguyễn Chí Thiện")))
                .isInstanceOf(RuntimeException.class);
    }

    // ── confirm ─────────────────────────────────────────────────────────

    private InstructorPayoutAccount pending() {
        InstructorPayoutAccount a = new InstructorPayoutAccount();
        a.setId(55L);
        a.setUser(instructor);
        a.setBankName("Vietcombank");
        a.setBankCode("VCB");
        a.setAccountLast4("6789");
        a.setAccountHash("hash-0123456789");
        a.setStatus(PayoutAccountStatus.PENDING_CONFIRM);
        when(accountRepository.findById(55L)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void confirm_firstAccount_isUsableImmediately() {
        InstructorPayoutAccount a = pending();
        LocalDateTime before = LocalDateTime.now();

        service.confirm(7L, 55L);

        assertThat(a.getStatus()).isEqualTo(PayoutAccountStatus.ACTIVE);
        assertThat(a.getUsableFrom()).isBetween(before, LocalDateTime.now().plusSeconds(1));
        verify(emailService, never()).sendPayoutAccountChangedEmail(any(), any(), any(), any());
    }

    @Test
    void confirm_replacingAnExistingAccount_locksWithdrawalsForThreeDays_andWarnsByEmail() {
        InstructorPayoutAccount previous = new InstructorPayoutAccount();
        previous.setStatus(PayoutAccountStatus.ACTIVE);
        previous.setAccountLast4("1111");
        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.of(previous));
        InstructorPayoutAccount a = pending();
        LocalDateTime before = LocalDateTime.now();

        service.confirm(7L, 55L);

        assertThat(previous.getStatus()).isEqualTo(PayoutAccountStatus.REPLACED);
        assertThat(a.getStatus()).isEqualTo(PayoutAccountStatus.ACTIVE);
        assertThat(a.getUsableFrom()).isBetween(before.plusDays(3), LocalDateTime.now().plusDays(3).plusSeconds(1));
        verify(emailService).sendPayoutAccountChangedEmail(eq(EMAIL), eq("Vietcombank"), eq("6789"), any());
    }

    @Test
    void confirm_byAnotherUser_orTwice_isRejected() {
        InstructorPayoutAccount a = pending();
        assertThatThrownBy(() -> service.confirm(999L, 55L)).isInstanceOf(AccessDeniedDomainException.class);
        assertThat(a.getStatus()).isEqualTo(PayoutAccountStatus.PENDING_CONFIRM);

        a.setStatus(PayoutAccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.confirm(7L, 55L)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void confirm_sameNumberAlreadyUsedByAnotherInstructor_alertsAdmins() {
        pending();
        when(accountRepository.existsByAccountHashAndStatusAndUser_IdNot("hash-0123456789", PayoutAccountStatus.ACTIVE, 7L)).thenReturn(true);
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        service.confirm(7L, 55L);

        verify(notificationService).notify(eq(1L), eq("PAYOUT_ACCOUNT_DUPLICATE"), anyString(), anyString(), anyString());
    }

    @Test
    void getCurrent_prefersActive_thenPending_elseNull() {
        InstructorPayoutAccount active = new InstructorPayoutAccount();
        active.setId(1L);
        active.setAccountLast4("1234");
        active.setStatus(PayoutAccountStatus.ACTIVE);
        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.of(active));
        assertThat(service.getCurrent(EMAIL).status()).isEqualTo(PayoutAccountStatus.ACTIVE);

        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.ACTIVE)).thenReturn(Optional.empty());
        when(accountRepository.findFirstByUser_IdAndStatus(7L, PayoutAccountStatus.PENDING_CONFIRM)).thenReturn(Optional.empty());
        assertThat(service.getCurrent(EMAIL)).isNull();
    }
}
