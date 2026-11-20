package com.lms.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.Role;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.service.SystemConfigService;
import com.lms.report.entity.ReportSnapshot;
import com.lms.report.repository.ReportSnapshotRepository;
import com.lms.report.service.FinanceReport.Reconciliation;
import com.lms.report.service.FinanceReport.Summary;
import com.lms.report.service.FinanceReportQueries;
import com.lms.report.service.FinanceReportService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceReportServiceTest {

    @Mock private FinanceReportQueries queries;
    @Mock private ReportSnapshotRepository snapshotRepository;
    @Mock private SystemConfigService config;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private FinanceReportService service;

    @BeforeEach
    void setUp() {
        service = new FinanceReportService(queries, snapshotRepository, config, userRepository, emailService, new ObjectMapper());
        lenient().when(queries.summary(any(), any())).thenReturn(new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        lenient().when(queries.buckets(any(), any(), any())).thenReturn(List.of());
        lenient().when(queries.courses(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        lenient().when(queries.instructors(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        lenient().when(queries.reconciliation(any(), any())).thenReturn(new Reconciliation(0, 0, 0, 0, 0, 0));
        lenient().when(snapshotRepository.save(any(ReportSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(emailService.sendReportEmail(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap())).thenReturn(true);
    }

    @Test
    void build_rejectsReversedAndTooLongRanges() {
        assertThatThrownBy(() -> service.build(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1), null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.build(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1), null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void generate_skipsWhenTheScheduledReportWasAlreadyMade() {
        when(snapshotRepository.existsByDedupeKey("DAILY:2026-11-19")).thenReturn(true);

        assertThat(service.generate("DAILY", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), "SCHEDULED", null, "DAILY:2026-11-19", List.of("a@b.com"))).isNull();

        verify(snapshotRepository, never()).save(any());
        verify(emailService, never()).sendReportEmail(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    void runScheduled_doesNothingBeforeSixInTheMorning() {
        assertThat(service.runScheduled(LocalDateTime.of(2026, 11, 20, 5, 59))).isZero();

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void runScheduled_makesWeeklyAndMonthlyOnlyWhenEnabledWithTheRightPeriodAndKeys() {
        when(config.getInt("REPORT_DAILY_ENABLED", 0)).thenReturn(0);
        when(config.getInt("REPORT_WEEKLY_ENABLED", 1)).thenReturn(1);
        when(config.getInt("REPORT_MONTHLY_ENABLED", 1)).thenReturn(1);
        when(config.getString("REPORT_RECIPIENTS", "")).thenReturn("a@b.com, bad, a@b.com, c@d.vn");
        when(snapshotRepository.existsByDedupeKey(anyString())).thenReturn(false);

        // Thứ Sáu 20/11/2026: tuần vừa xong là 09/11–15/11, tháng vừa xong là 10/2026.
        int made = service.runScheduled(LocalDateTime.of(2026, 11, 20, 7, 0));

        assertThat(made).isEqualTo(2);
        ArgumentCaptor<ReportSnapshot> snaps = ArgumentCaptor.forClass(ReportSnapshot.class);
        verify(snapshotRepository, org.mockito.Mockito.atLeast(2)).save(snaps.capture());
        assertThat(snaps.getAllValues()).extracting(ReportSnapshot::getDedupeKey).contains("WEEKLY:2026-11-09", "MONTHLY:2026-10-01");
        // 2 người nhận hợp lệ (trùng và sai định dạng bị bỏ) × 2 báo cáo = 4 email.
        verify(emailService, times(4)).sendReportEmail(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    void recipients_fallsBackToActiveAdminsWhenNotConfigured() {
        when(config.getString("REPORT_RECIPIENTS", "")).thenReturn("");
        User admin = new User();
        admin.setEmail("quantri@gmail.com");
        admin.setRole(Role.ADMIN);
        admin.setIsActive(true);
        // Admin nội bộ (.local) không có hộp thư thật: bị bỏ qua để thư không bị trả lại.
        User internal = new User();
        internal.setEmail("admin@lms.local");
        internal.setRole(Role.ADMIN);
        internal.setIsActive(true);
        User off = new User();
        off.setEmail("old@lms.local");
        off.setIsActive(false);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin, internal, off));

        assertThat(service.recipients()).containsExactly("quantri@gmail.com");
    }
}
