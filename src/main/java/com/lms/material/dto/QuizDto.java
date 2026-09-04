package com.lms.material.dto;

import java.time.LocalDateTime;

public class QuizDto {

    public record QuizSettingsReq(
            Integer randomPickCount,
            Boolean allowReview,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer durationMinutes,
            Integer maxAttempts,
            Boolean isProctored,
            Integer maxViolations
    ) {}

}
