package com.lms.auth.dto;

import java.util.List;

public record StreakResponse(
        int currentStreak,
        int longestStreak,
        boolean hasStudiedToday,
        List<String> learningDays,
        /** Streak Freeze (03/10/2026) — số lần đóng băng còn lại trong tháng hiện tại. */
        int freezesRemaining,
        /** true nếu streak VỪA được cứu bằng 1 lần đóng băng tự động ở request này. */
        boolean justFrozen
) {}
