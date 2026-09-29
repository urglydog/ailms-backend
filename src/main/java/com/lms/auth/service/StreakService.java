package com.lms.auth.service;

import com.lms.auth.dto.StreakResponse;
import com.lms.auth.entity.User;
import com.lms.auth.entity.UserLearningDay;
import com.lms.auth.entity.UserStreak;
import com.lms.auth.repository.UserLearningDayRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {

    private final UserStreakRepository userStreakRepository;
    private final UserLearningDayRepository userLearningDayRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordActivity(Long userId, Instant completedAt) {
        UserStreak streak = userStreakRepository.findById(userId).orElseGet(() -> createInitialStreak(userId));
        ZoneId zone;
        try {
            zone = ZoneId.of(streak.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        LocalDate todayInUserZone = LocalDate.ofInstant(completedAt, zone);

        // Record learning day
        try {
            UserLearningDay learningDay = new UserLearningDay();
            learningDay.setUser(streak.getUser());
            learningDay.setActivityDate(todayInUserZone);
            userLearningDayRepository.saveAndFlush(learningDay);
        } catch (DataIntegrityViolationException e) {
            log.info("Learning day already recorded for user {} on date {}", userId, todayInUserZone);
            return;
        }

        // Calculate new streak
        LocalDate lastActivity = streak.getLastActivityDate();
        if (lastActivity == null || todayInUserZone.isEqual(lastActivity.plusDays(1))) {
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }
        } else if (todayInUserZone.isAfter(lastActivity.plusDays(1))) {
            streak.setCurrentStreak(1);
        }
        
        streak.setLastActivityDate(todayInUserZone);
        userStreakRepository.save(streak);
    }

    @Transactional
    public StreakResponse getStreakByEmail(String email, String clientTimezone) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return getStreak(user.getId(), clientTimezone);
    }

    @Transactional
    public StreakResponse getStreak(Long userId, String clientTimezone) {
        UserStreak streak = userStreakRepository.findById(userId).orElseGet(() -> createInitialStreak(userId));
        
        if (clientTimezone != null && !clientTimezone.isEmpty() && !clientTimezone.equals(streak.getTimezone())) {
            try {
                ZoneId.of(clientTimezone);
                streak.setTimezone(clientTimezone);
                userStreakRepository.save(streak);
            } catch (Exception e) {
                log.warn("Invalid timezone from client: {}", clientTimezone);
            }
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(streak.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        LocalDate todayInUserZone = LocalDate.ofInstant(Instant.now(), zone);

        boolean changed = false;
        if (streak.getLastActivityDate() != null && todayInUserZone.isAfter(streak.getLastActivityDate().plusDays(1))) {
            streak.setCurrentStreak(0);
            changed = true;
        }
        
        if (changed) {
            userStreakRepository.save(streak);
        }

        boolean hasStudiedToday = streak.getLastActivityDate() != null && streak.getLastActivityDate().isEqual(todayInUserZone);
        
        LocalDate startDate = todayInUserZone.minusDays(6);
        List<LocalDate> learningDays = userLearningDayRepository
                .findByUser_IdAndActivityDateBetweenOrderByActivityDateDesc(userId, startDate, todayInUserZone)
                .stream()
                .map(UserLearningDay::getActivityDate)
                .toList();

        List<String> formattedLearningDays = learningDays.stream().map(d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE)).toList();

        return new StreakResponse(streak.getCurrentStreak(), streak.getLongestStreak(), hasStudiedToday, formattedLearningDays);
    }

    private UserStreak createInitialStreak(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserStreak streak = new UserStreak();
        streak.setUserId(userId);
        streak.setUser(user);
        return userStreakRepository.save(streak);
    }
}
