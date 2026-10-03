-- Streak Freeze (UpComming_Plan.md Sprint 2 mục 5) — tự động bảo toàn streak khi lỡ đúng 1
-- ngày, giới hạn số lần/tháng. Reset giới hạn theo tháng dương lịch của múi giờ user (đã có
-- sẵn ở cột `timezone`), không cộng dồn qua tháng.
ALTER TABLE user_streaks
    ADD COLUMN freeze_used_count INT NOT NULL DEFAULT 0,
    ADD COLUMN freeze_reset_month VARCHAR(7) DEFAULT NULL,
    -- Chống gửi lặp nhắc nhở streak nhiều lần trong 1 ngày khi cron chạy theo giờ
    -- (StreakReminderJob) — xem com.lms.auth.service.StreakReminderJob.
    ADD COLUMN last_reminder_sent_date DATE DEFAULT NULL;
