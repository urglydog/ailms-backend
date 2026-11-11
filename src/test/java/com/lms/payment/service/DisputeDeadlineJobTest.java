package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.PaymentDisputeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisputeDeadlineJobTest {

    @Mock private PaymentDisputeRepository disputeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private DisputeDeadlineJob job;

    private PaymentDispute expiredDispute(String txnRef) {
        Payment payment = new Payment();
        payment.setTxnRef(txnRef);
        PaymentDispute d = new PaymentDispute();
        d.setPayment(payment);
        return d;
    }

    @Test
    void markExpired_marksNoResponseAndNotifiesEveryAdminOncePerDispute() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);
        PaymentDispute d1 = expiredDispute("t1");
        PaymentDispute d2 = expiredDispute("t2");
        User admin1 = new User();
        admin1.setId(1L);
        admin1.setRole(Role.ADMIN);
        User admin2 = new User();
        admin2.setId(2L);
        admin2.setRole(Role.ADMIN);
        when(disputeRepository.findByInstructorDecisionIsNullAndInstructorDeadlineAtBeforeAndStatusIn(eq(now), any()))
                .thenReturn(List.of(d1, d2));
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin1, admin2));

        int marked = job.markExpired(now);

        assertThat(marked).isEqualTo(2);
        assertThat(d1.getInstructorDecision()).isEqualTo(InstructorDecision.NO_RESPONSE);
        assertThat(d2.getInstructorDecision()).isEqualTo(InstructorDecision.NO_RESPONSE);
        verify(disputeRepository, times(2)).save(any(PaymentDispute.class));
        verify(notificationService, times(4)).notify(any(), eq("DISPUTE_INSTRUCTOR_NO_RESPONSE"), anyString(), anyString(), anyString());
    }

    @Test
    void markExpired_nothingExpired_returnsZeroWithoutQueryingAdmins() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);
        when(disputeRepository.findByInstructorDecisionIsNullAndInstructorDeadlineAtBeforeAndStatusIn(eq(now), any()))
                .thenReturn(List.of());

        assertThat(job.markExpired(now)).isZero();

        verify(userRepository, never()).findByRole(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
