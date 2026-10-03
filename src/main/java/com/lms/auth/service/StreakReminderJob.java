package com.lms.auth.service;

import com.lms.auth.entity.UserStreak;
import com.lms.auth.repository.UserStreakRepository;
import com.lms.common.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * UpComming_Plan.md Sprint 2 mục 5 (03/10/2026) — "thông báo nhắc nhở giữ chuỗi", phần AC duy
 * nhất của tính năng Streak CHƯA được code dù mục này đã bị đánh dấu ĐÃ HOÀN THÀNH.
 *
 * <p>Vì mỗi user có {@code timezone} riêng (xem {@link UserStreak#getTimezone()}), không thể
 * dùng 1 mốc cron UTC/VN cố định cho tất cả — job này chạy MỖI GIỜ, với mỗi user tự tính giờ
 * local của họ và chỉ gửi khi rơi vào khung giờ nhắc nhở (20h-21h local), đúng streak đang có
 * nguy cơ mất (đã học hôm qua, CHƯA học hôm nay), và chưa gửi nhắc nhở nào hôm nay (chống job
 * theo giờ gửi lặp nhiều lần trong khung 1 giờ nếu bị trigger nhiều lần).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreakReminderJob {

    private static final int REMINDER_HOUR_LOCAL = 20;

    private final UserStreakRepository userStreakRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void sendReminders() {
        List<UserStreak> streaks = userStreakRepository.findAll();
        int sent = 0;
        for (UserStreak streak : streaks) {
            try {
                if (shouldRemind(streak)) {
                    notifyAndMark(streak);
                    sent++;
                }
            } catch (Exception e) {
                // Best-effort — 1 user lỗi (vd timezone hỏng) không được chặn các user khác.
                log.error("Loi gui streak reminder cho user {}", streak.getUserId(), e);
            }
        }
        if (sent > 0) {
            log.info("Da gui {} streak reminder", sent);
        }
    }

    private boolean shouldRemind(UserStreak streak) {
        if (streak.getCurrentStreak() == null || streak.getCurrentStreak() <= 0) return false;
        if (streak.getLastActivityDate() == null) return false;

        ZoneId zone;
        try {
            zone = ZoneId.of(streak.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        ZonedDateTime nowLocal = Instant.now().atZone(zone);
        if (nowLocal.getHour() != REMINDER_HOUR_LOCAL) return false;

        LocalDate today = nowLocal.toLocalDate();
        LocalDate yesterday = today.minusDays(1);

        boolean hasStudiedToday = streak.getLastActivityDate().isEqual(today);
        boolean streakStillAlive = streak.getLastActivityDate().isEqual(yesterday) || streak.getLastActivityDate().isEqual(today);
        boolean alreadyRemindedToday = today.equals(streak.getLastReminderSentDate());

        return !hasStudiedToday && streakStillAlive && !alreadyRemindedToday;
    }

    private void notifyAndMark(UserStreak streak) {
        notificationService.notify(
                streak.getUserId(),
                "STREAK_REMINDER",
                "Giữ chuỗi " + streak.getCurrentStreak() + " ngày của bạn!",
                "Bạn chưa học hôm nay — học 1 bài hoặc làm 1 quiz trước nửa đêm để không mất chuỗi " + streak.getCurrentStreak() + " ngày nhé!",
                "/my-courses"
        );
        streak.setLastReminderSentDate(currentLocalDate(streak));
        userStreakRepository.save(streak);
    }

    private LocalDate currentLocalDate(UserStreak streak) {
        ZoneId zone;
        try {
            zone = ZoneId.of(streak.getTimezone());
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        return Instant.now().atZone(zone).toLocalDate();
    }
}
