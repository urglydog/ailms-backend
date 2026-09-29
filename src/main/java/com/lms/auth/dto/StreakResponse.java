package com.lms.auth.dto;

import java.util.List;

public record StreakResponse(
        int currentStreak,
        int longestStreak,
        boolean hasStudiedToday,
        List<String> learningDays
) {}
