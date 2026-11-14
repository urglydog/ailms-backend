package com.lms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.payment.entity.RefundRateAlert;
import com.lms.payment.repository.PaymentRepository;
import com.lms.payment.repository.RefundRateAlertRepository;
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
class RefundRateFlagJobTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRateAlertRepository alertRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private SystemConfigService config;
    @InjectMocks private RefundRateFlagJob job;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        Course c = new Course();
        c.setId(10L);
        c.setTitle("Unity");
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        lenient().when(courseRepository.findById(10L)).thenReturn(Optional.of(c));
        lenient().when(config.getInt("REFUND_FLAG_MIN_COUNT", 3)).thenReturn(3);
        lenient().when(config.getInt("REFUND_FLAG_RATE_PCT", 20)).thenReturn(20);
    }

    private void rows(long total, long refunded) {
        when(paymentRepository.refundRateByCourse(any(), eq(3))).thenReturn(List.<Object[]>of(new Object[] {10L, total, refunded}));
    }

    @Test
    void flagsACourseAtOrAboveTheRateOnce_thenStaysQuietForThirtyDays() {
        rows(10, 3);
        when(alertRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(job.scan(LocalDateTime.now())).isEqualTo(1);
        verify(notificationService).notify(eq(1L), eq("REFUND_RATE_HIGH"), anyString(), anyString(), anyString());

        RefundRateAlert recent = new RefundRateAlert();
        recent.setCourseId(10L);
        recent.setAlertedAt(LocalDateTime.now().minusDays(2));
        when(alertRepository.findById(10L)).thenReturn(Optional.of(recent));
        assertThat(job.scan(LocalDateTime.now())).isZero();
    }

    @Test
    void doesNotFlagALowRate() {
        rows(100, 3); // 3% < 20%

        assertThat(job.scan(LocalDateTime.now())).isZero();
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), anyString());
    }
}
