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
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreakService {

    private final UserStreakRepository userStreakRepository;
    private final UserLearningDayRepository userLearningDayRepository;
    private final UserRepository userRepository;

    /** Streak Freeze (UpComming_Plan.md Sprint 2 mục 5, 03/10/2026) — số lần đóng băng tự động
     * tối đa mỗi tháng. Mỗi lần chỉ cứu được ĐÚNG 1 ngày bị lỡ (không cứu được khoảng trống
     * nhiều ngày liên tiếp trong 1 lần) — giống quy ước phổ biến của các app học ngoại ngữ. */
    private static final int FREEZE_LIMIT_PER_MONTH = 2;
    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

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
        String currentMonthKey = todayInUserZone.format(MONTH_KEY_FORMAT);
        if (!currentMonthKey.equals(streak.getFreezeResetMonth())) {
            streak.setFreezeResetMonth(currentMonthKey);
            streak.setFreezeUsedCount(0);
            changed = true;
        }

        boolean justFrozen = false;
        LocalDate lastActivity = streak.getLastActivityDate();
        // BUG THẬT (03/10/2026) — nếu currentStreak ĐÃ về 0 từ trước (streak đã vỡ ở lần kiểm tra
        // trước đó, nhưng lastActivityDate cũ vẫn còn giữ nguyên vì nhánh reset không cập nhật nó),
        // daysMissed tính từ lastActivityDate cũ vẫn có thể TRÙNG 1 vào 1 ngày sau đó một cách
        // ngẫu nhiên theo lịch, khiến freeze bị áp dụng "cứu" một streak ĐÃ CHẾT (currentStreak vẫn
        // là 0 sau khi freeze vì nhánh freeze không đổi currentStreak) — tốn 1 lượt freeze free và
        // hiện thông báo "đã đóng băng" vô nghĩa (chuỗi 0 ngày). Chỉ áp freeze khi THỰC SỰ có
        // streak đang sống (>0) để bảo toàn.
        boolean hasActiveStreakToProtect = streak.getCurrentStreak() != null && streak.getCurrentStreak() > 0;
        if (lastActivity != null && todayInUserZone.isAfter(lastActivity.plusDays(1))) {
            long daysMissed = ChronoUnit.DAYS.between(lastActivity, todayInUserZone) - 1;
            if (hasActiveStreakToProtect && daysMissed == 1 && streak.getFreezeUsedCount() < FREEZE_LIMIT_PER_MONTH) {
                // Streak Freeze tự động: đúng 1 ngày bị lỡ và còn hạn mức tháng này → coi ngày
                // đó như "đã đóng băng", đẩy lastActivityDate lên 1 ngày để lần học tiếp theo
                // vẫn nối chuỗi liên tục, không reset currentStreak.
                streak.setFreezeUsedCount(streak.getFreezeUsedCount() + 1);
                streak.setLastActivityDate(lastActivity.plusDays(1));
                justFrozen = true;
                log.info("Streak freeze tu dong ap dung cho user {} (con {} lan/thang)", userId,
                        FREEZE_LIMIT_PER_MONTH - streak.getFreezeUsedCount());
            } else {
                streak.setCurrentStreak(0);
            }
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

        int freezesRemaining = FREEZE_LIMIT_PER_MONTH - streak.getFreezeUsedCount();
        return new StreakResponse(streak.getCurrentStreak(), streak.getLongestStreak(), hasStudiedToday, formattedLearningDays, freezesRemaining, justFrozen);
    }

    private UserStreak createInitialStreak(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserStreak streak = new UserStreak();
        streak.setUserId(userId);
        streak.setUser(user);
        return userStreakRepository.save(streak);
    }
}
