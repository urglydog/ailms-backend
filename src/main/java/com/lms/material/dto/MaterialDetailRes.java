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
        Long quizId, // ID entity Quiz thật (khác `id` = MaterialGeneration) — Only populated for QUIZ,
                    // cần cho các endpoint /api/v1/quizzes/{quizId}/... (export-pdf...)
        Boolean isOfficial // Đã tính sẵn trong service (mindmap/deck/quiz.getIsOfficial()) nhưng
                            // trước đây KHÔNG đưa vào response — FE cần field này để quyết định có
                            // cho chỉnh sửa (QuizPersonalEditor) hay không, KHÔNG được tự suy luận
                            // phía client (12/10/2026, mobile port QuizPersonalEditor).
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
            String topicTag,
            Integer videoTimestamp,
            Long referenceLessonId,
            List<QuizOptionDto> options
    ) {}

    @Builder
    public record QuizOptionDto(
            Long id,
            String content,
            Boolean isCorrect
    ) {}
}
