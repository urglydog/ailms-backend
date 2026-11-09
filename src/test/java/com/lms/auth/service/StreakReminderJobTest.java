package com.lms.auth.service;

import com.lms.auth.entity.UserStreak;
import com.lms.auth.repository.UserStreakRepository;
import com.lms.common.service.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hiệu năng đợt 3: job nhắc streak chỉ đọc streak có khả năng cần nhắc, theo từng lô — không nạp cả bảng mỗi giờ. */
@ExtendWith(MockitoExtension.class)
class StreakReminderJobTest {

    @Mock private UserStreakRepository userStreakRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private StreakReminderJob job;

    /** Múi giờ mà "bây giờ" đang là 20h — khung giờ job gửi nhắc nhở. */
    private static ZoneId zoneWhereItIsEightPm() {
        int utcHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        int offset = Math.floorMod(20 - utcHour, 24);
        if (offset > 14) offset -= 24;
        return ZoneOffset.ofHours(offset);
    }

    private static UserStreak streak(long userId, ZoneId zone, LocalDate lastActivity, int current) {
        UserStreak s = new UserStreak();
        s.setUserId(userId);
        s.setTimezone(zone.getId());
        s.setLastActivityDate(lastActivity);
        s.setCurrentStreak(current);
        return s;
    }

    @Test
    void sendReminders_remindsStreakAtRisk_readingCandidatesOnly() {
        ZoneId zone = zoneWhereItIsEightPm();
        LocalDate localToday = Instant.now().atZone(zone).toLocalDate();
        UserStreak atRisk = streak(5L, zone, localToday.minusDays(1), 4);
        when(userStreakRepository.findReminderCandidates(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(atRisk), PageRequest.of(0, 500), false));

        job.sendReminders();

        verify(notificationService).notify(eq(5L), eq("STREAK_REMINDER"), anyString(), anyString(), eq("/my-courses"));
        assertThat(atRisk.getLastReminderSentDate()).isEqualTo(localToday);
        verify(userStreakRepository, never()).findAll();
    }

    @Test
    void sendReminders_studiedToday_isNotReminded() {
        ZoneId zone = zoneWhereItIsEightPm();
        LocalDate localToday = Instant.now().atZone(zone).toLocalDate();
        when(userStreakRepository.findReminderCandidates(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(streak(6L, zone, localToday, 2)), PageRequest.of(0, 500), false));

        job.sendReminders();

        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendReminders_walksEveryPage() {
        ZoneId zone = zoneWhereItIsEightPm();
        LocalDate yesterday = Instant.now().atZone(zone).toLocalDate().minusDays(1);
        when(userStreakRepository.findReminderCandidates(any(LocalDate.class), eq(PageRequest.of(0, 500))))
                .thenReturn(new SliceImpl<>(List.of(streak(1L, zone, yesterday, 1)), PageRequest.of(0, 500), true));
        when(userStreakRepository.findReminderCandidates(any(LocalDate.class), eq(PageRequest.of(1, 500))))
                .thenReturn(new SliceImpl<>(List.of(streak(2L, zone, yesterday, 1)), PageRequest.of(1, 500), false));

        job.sendReminders();

        verify(notificationService).notify(eq(1L), anyString(), anyString(), anyString(), any());
        verify(notificationService).notify(eq(2L), anyString(), anyString(), anyString(), any());
    }
}
