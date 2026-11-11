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
}
