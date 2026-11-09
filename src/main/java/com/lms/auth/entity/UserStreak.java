package com.lms.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_streaks")
@Getter
@Setter
public class UserStreak {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private Integer longestStreak = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";

    /** Streak Freeze (03/10/2026) — số lần đã dùng trong tháng hiện tại ({@link #freezeResetMonth}). */
    @Column(name = "freeze_used_count", nullable = false)
    private Integer freezeUsedCount = 0;

    /** Tháng mà {@link #freezeUsedCount} đang tính cho, dạng "yyyy-MM" theo {@link #timezone} của user. */
    @Column(name = "freeze_reset_month", length = 7)
    private String freezeResetMonth;

    /** Streak reminder (03/10/2026) — chống {@code StreakReminderJob} gửi lặp nhiều lần/ngày
     * khi cron chạy theo giờ. */
    @Column(name = "last_reminder_sent_date")
    private LocalDate lastReminderSentDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
