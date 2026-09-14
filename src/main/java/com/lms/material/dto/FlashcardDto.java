package com.lms.material.dto;

import java.time.LocalDateTime;

public class FlashcardDto {
    public record ReviewReq(
        Integer quality // 0 to 5
    ) {}

    public record ReviewRes(
        Long flashcardId,
        LocalDateTime nextReviewAt,
        Integer intervalDays,
        Integer repetitions,
        java.math.BigDecimal easiness
    ) {}

    public record CardWithReview(
        Long id,
        String frontText,
        String backText,
        LocalDateTime nextReviewAt,
        Integer intervalDays,
        Integer repetitions,
        java.math.BigDecimal easiness,
        boolean isDue // true if nextReviewAt <= today
    ) {}

    public record UpdateReq(
        String frontText,
        String backText
    ) {}

    public record AddReq(
        String frontText,
        String backText
    ) {}
}
