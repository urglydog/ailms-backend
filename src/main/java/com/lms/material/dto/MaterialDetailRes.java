package com.lms.material.dto;

import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record MaterialDetailRes(
        Long id,
        MaterialType materialType,
        String language,
        String title,
        Integer versionNo,
        GenStatus status,
        Long lessonId,
        String quizType,
        LocalDateTime createdAt,
        String mermaidCode, // Only populated for MINDMAP
        List<FlashcardDto> flashcards, // Only populated for FLASHCARD
        List<QuizQuestionDto> quizQuestions, // Only populated for QUIZ
        Long quizId // ID entity Quiz thật (khác `id` = MaterialGeneration) — Only populated for QUIZ,
                    // cần cho các endpoint /api/v1/quizzes/{quizId}/... (export-pdf...)
) {
    @Builder
    public record FlashcardDto(
            Long id,
            String frontText,
            String backText,
            LocalDateTime nextReviewAt,
            Integer intervalDays,
            Integer repetitions,
            java.math.BigDecimal easiness,
            Boolean isDue
    ) {}

    @Builder
    public record QuizQuestionDto(
            Long id,
            String content,
            Integer displayOrder,
            List<QuizOptionDto> options
    ) {}

    @Builder
    public record QuizOptionDto(
            Long id,
            String content,
            Boolean isCorrect
    ) {}
}
