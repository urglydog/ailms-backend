package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.LedgerEntryType;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorLedgerEntry;
import com.lms.finance.repository.InstructorLedgerEntryRepository;
import com.lms.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private InstructorLedgerEntryRepository repository;
    @Mock private SystemConfigService systemConfigService;

    @InjectMocks
    private LedgerService ledgerService;

    private User instructor;
    private Payment payment;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(5L);
        Course course = new Course();
        course.setId(10L);
        course.setInstructor(instructor);

        payment = new Payment();
        payment.setId(100L);
        payment.setTxnRef("txn-1");
        payment.setCourse(course);
        payment.setInstructorEarning(new BigDecimal("74000"));
        payment.setPaidAt(LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    private void stubHold(int window, int extra) {
        when(systemConfigService.getInt(eq("REFUND_WINDOW_DAYS"), anyInt())).thenReturn(window);
        when(systemConfigService.getInt(eq("PAYOUT_HOLD_EXTRA_DAYS"), anyInt())).thenReturn(extra);
    }

    @Test
    void recordEarning_createsPositiveEntryAvailableAfterRefundWindowPlusExtra() {
        stubHold(7, 3);
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING)).thenReturn(false);

        ledgerService.recordEarning(payment);

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        InstructorLedgerEntry e = captor.getValue();
        assertThat(e.getInstructor()).isSameAs(instructor);
        assertThat(e.getEntryType()).isEqualTo(LedgerEntryType.EARNING);
        assertThat(e.getAmount()).isEqualByComparingTo("74000");
        assertThat(e.getAvailableAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 9, 0)); // +10 ngày
        assertThat(e.getPayment()).isSameAs(payment);
    }

    @Test
    void recordEarning_usesCurrentConfigForHoldDays() {
        stubHold(14, 3);
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING)).thenReturn(false);

        ledgerService.recordEarning(payment);

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAvailableAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0)); // +17 ngày
    }

    @Test
    void recordEarning_alreadyRecorded_isIdempotent() {
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING)).thenReturn(true);

        ledgerService.recordEarning(payment);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordEarning_zeroOrNullEarning_isSkipped() {
        payment.setInstructorEarning(BigDecimal.ZERO);
        ledgerService.recordEarning(payment);
        payment.setInstructorEarning(null);
        ledgerService.recordEarning(payment);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── recordRefundReversal (11/11/2026, Giai đoạn B) ─────────────────────────────────────

    private InstructorLedgerEntry earningEntry(long id, LocalDateTime availableAt) {
        InstructorLedgerEntry e = new InstructorLedgerEntry();
        e.setId(id);
        e.setEntryType(LedgerEntryType.EARNING);
        e.setAmount(new BigDecimal("74000"));
        e.setAvailableAt(availableAt);
        return e;
    }

    @Test
    void recordRefundReversal_whileEarningStillHeld_inheritsItsAvailableAtSoBothNetOut() {
        LocalDateTime heldUntil = LocalDateTime.now().plusDays(5);
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.REFUND_REVERSAL)).thenReturn(false);
        when(repository.findByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING))
                .thenReturn(java.util.Optional.of(earningEntry(7L, heldUntil)));

        ledgerService.recordRefundReversal(payment, new BigDecimal("74000"), 9L);

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        InstructorLedgerEntry e = captor.getValue();
        assertThat(e.getEntryType()).isEqualTo(LedgerEntryType.REFUND_REVERSAL);
        assertThat(e.getAmount()).isEqualByComparingTo("-74000");
        assertThat(e.getAvailableAt()).isEqualTo(heldUntil);
        assertThat(e.getRefEntryId()).isEqualTo(7L);
        assertThat(e.getCreatedByUserId()).isEqualTo(9L);
        assertThat(e.getInstructor()).isSameAs(instructor);
    }

    @Test
    void recordRefundReversal_earningAlreadyMatured_takesEffectImmediately() {
        LocalDateTime before = LocalDateTime.now();
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.REFUND_REVERSAL)).thenReturn(false);
        when(repository.findByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING))
                .thenReturn(java.util.Optional.of(earningEntry(7L, before.minusDays(3))));

        ledgerService.recordRefundReversal(payment, new BigDecimal("74000"), 9L);

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAvailableAt()).isBetween(before, LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void recordRefundReversal_chargeLargerThanEarning_recordsFullCharge() {
        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.REFUND_REVERSAL)).thenReturn(false);
        when(repository.findByPayment_IdAndEntryType(100L, LedgerEntryType.EARNING)).thenReturn(java.util.Optional.empty());

        ledgerService.recordRefundReversal(payment, new BigDecimal("200000"), null);

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-200000");
        assertThat(captor.getValue().getRefEntryId()).isNull();
    }

    @Test
    void recordRefundReversal_zeroOrNullCharge_orAlreadyRecorded_writesNothing() {
        ledgerService.recordRefundReversal(payment, BigDecimal.ZERO, 9L);
        ledgerService.recordRefundReversal(payment, null, 9L);

        when(repository.existsByPayment_IdAndEntryType(100L, LedgerEntryType.REFUND_REVERSAL)).thenReturn(true);
        ledgerService.recordRefundReversal(payment, new BigDecimal("74000"), 9L);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ── Số dư & giữ chỗ rút tiền (Giai đoạn C) ─────────────────────────────────────────────

    @Test
    void getBalance_withdrawableIsAvailableMinusFrozen_neverNegative() {
        when(repository.sumAvailable(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("500000"));
        when(repository.sumPending(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("120000"));
        when(repository.sumFrozen(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("150000"));

        LedgerService.Balance b = ledgerService.getBalance(5L);

        assertThat(b.available()).isEqualByComparingTo("500000");
        assertThat(b.pending()).isEqualByComparingTo("120000");
        assertThat(b.frozen()).isEqualByComparingTo("150000");
        assertThat(b.withdrawable()).isEqualByComparingTo("350000");
    }

    @Test
    void getBalance_negativeAvailableAfterRefundOfPaidOutMoney_hasZeroWithdrawable() {
        when(repository.sumAvailable(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("-74000"));
        when(repository.sumPending(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO);
        when(repository.sumFrozen(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO);

        LedgerService.Balance b = ledgerService.getBalance(5L);

        assertThat(b.available()).isEqualByComparingTo("-74000");
        assertThat(b.withdrawable()).isEqualByComparingTo("0");
    }

    @Test
    void recordPayoutReserve_writesNegativeEntryForThePayout_idempotently() {
        when(repository.existsByPayoutIdAndEntryType(90L, LedgerEntryType.PAYOUT_RESERVE)).thenReturn(false);

        ledgerService.recordPayoutReserve(instructor, new BigDecimal("200000"), 90L, "PO-1");

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.PAYOUT_RESERVE);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-200000");
        assertThat(captor.getValue().getPayoutId()).isEqualTo(90L);

        when(repository.existsByPayoutIdAndEntryType(91L, LedgerEntryType.PAYOUT_RESERVE)).thenReturn(true);
        ledgerService.recordPayoutReserve(instructor, new BigDecimal("1"), 91L, "PO-2");
        verify(repository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordPayoutRelease_writesPositiveEntry_idempotently() {
        when(repository.existsByPayoutIdAndEntryType(90L, LedgerEntryType.PAYOUT_RELEASE)).thenReturn(false);
        ledgerService.recordPayoutRelease(instructor, new BigDecimal("200000"), 90L, "PO-1");

        ArgumentCaptor<InstructorLedgerEntry> captor = ArgumentCaptor.forClass(InstructorLedgerEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.PAYOUT_RELEASE);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("200000");

        when(repository.existsByPayoutIdAndEntryType(90L, LedgerEntryType.PAYOUT_RELEASE)).thenReturn(true);
        ledgerService.recordPayoutRelease(instructor, new BigDecimal("200000"), 90L, "PO-1");
        verify(repository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
    }
}
