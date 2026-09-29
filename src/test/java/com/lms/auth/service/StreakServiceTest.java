package com.lms.auth.service;

import com.lms.auth.dto.StreakResponse;
import com.lms.auth.entity.User;
import com.lms.auth.entity.UserLearningDay;
import com.lms.auth.entity.UserStreak;
import com.lms.auth.repository.UserLearningDayRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.repository.UserStreakRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock
    private UserStreakRepository userStreakRepository;
    @Mock
    private UserLearningDayRepository userLearningDayRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StreakService streakService;

    private User user;
    private UserStreak streak;
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@lms.local");

        streak = new UserStreak();
        streak.setUserId(1L);
        streak.setUser(user);
        streak.setCurrentStreak(5);
        streak.setLongestStreak(5);
        streak.setTimezone("Asia/Ho_Chi_Minh");
    }

    @Test
    void recordActivity_incrementsStreak_whenYesterdayActivity() {
        streak.setLastActivityDate(LocalDate.now(zone).minusDays(1)); // studied yesterday
        when(userStreakRepository.findById(1L)).thenReturn(Optional.of(streak));
        when(userLearningDayRepository.saveAndFlush(any(UserLearningDay.class))).thenAnswer(i -> i.getArguments()[0]);

        streakService.recordActivity(1L, Instant.now());

        verify(userStreakRepository).save(streak);
        assertThat(streak.getCurrentStreak()).isEqualTo(6);
        assertThat(streak.getLongestStreak()).isEqualTo(6);
        assertThat(streak.getLastActivityDate()).isEqualTo(LocalDate.now(zone));
    }
    
    @Test
    void recordActivity_ignores_whenDataIntegrityViolation() {
        streak.setLastActivityDate(LocalDate.now(zone).minusDays(1));
        when(userStreakRepository.findById(1L)).thenReturn(Optional.of(streak));
        when(userLearningDayRepository.saveAndFlush(any(UserLearningDay.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        streakService.recordActivity(1L, Instant.now());

        verify(userStreakRepository, never()).save(streak);
        assertThat(streak.getCurrentStreak()).isEqualTo(5); // Not changed
    }
}
