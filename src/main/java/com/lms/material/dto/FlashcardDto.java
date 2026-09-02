package com.lms.material.dto;

import java.time.LocalDate;

public class FlashcardDto {
    public record ReviewReq(
        Integer quality // 0 to 5
    ) {}

    public record ReviewRes(
        Long flashcardId,
        LocalDate nextReviewAt,
        Integer intervalDays,
        Integer repetitions,
        java.math.BigDecimal easiness
    ) {}

    public record CardWithReview(
        Long id,
        String frontText,
        String backText,
        LocalDate nextReviewAt,
        Integer intervalDays,
        Integer repetitions,
        java.math.BigDecimal easiness,
        boolean isDue // true if nextReviewAt <= today
    ) {}
}
