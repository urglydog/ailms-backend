package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceMaintenanceJobTest {

    @Mock private PayoutService payoutService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private LedgerService ledgerService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private FinanceMaintenanceJob job;

    @Test
    void reconcileMissingEarnings_recordsEachMissingPaymentAndReportsTheCount() {
        Payment p1 = new Payment();
        Payment p2 = new Payment();
        when(paymentRepository.findPaidMissingLedgerEarning(any())).thenReturn(List.of(p1, p2));

        assertThat(job.reconcileMissingEarnings()).isEqualTo(2);

        verify(ledgerService).recordEarning(p1);
        verify(ledgerService).recordEarning(p2);
    }

    @Test
    void reconcileLedger_alertsAdminsOnlyWhenSomethingWasHealed() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(paymentRepository.findPaidMissingLedgerEarning(any())).thenReturn(List.of(new Payment()));
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        job.reconcileLedger();

        verify(notificationService).notify(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void reconcileLedger_nothingMissing_isSilent() {
        when(paymentRepository.findPaidMissingLedgerEarning(any())).thenReturn(List.of());

        job.reconcileLedger();

        verify(ledgerService, never()).recordEarning(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void jobsNeverThrow_soTheSchedulerKeepsRunning() {
        doThrow(new IllegalStateException("db")).when(payoutService).expireUnconfirmed(any());
        when(paymentRepository.findPaidMissingLedgerEarning(any())).thenThrow(new IllegalStateException("db"));

        job.expireUnconfirmedPayouts();
        job.reconcileLedger();

        verify(payoutService, times(1)).expireUnconfirmed(any());
    }
}
