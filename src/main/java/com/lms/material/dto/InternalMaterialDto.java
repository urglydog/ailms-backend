package com.lms.material.dto;

import java.util.List;
import lombok.Builder;

public class InternalMaterialDto {
    @Builder
    public record GenerationContextRes(
            Long generationId,
            Long courseId,
            String courseTitle,
            String materialType,
            String language,
            String scopeType,
            Long scopeRefId,
            String quantityLevel,
            String difficultyLevel,
            List<TranscriptSegmentDto> transcripts
    ) {}

    @Builder
    public record TranscriptSegmentDto(
            String text,
            Double startSec,
            Double endSec
    ) {}

    public record FinishReq(
            String outcome,
            String errorMessage,
            String mermaidCode,
            List<FlashcardDto> flashcards,
            List<QuizDto> quizzes,
            UsageMetadataDto usageMetadata
    ) {}
    
    public record UsageMetadataDto(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {}

    public record FlashcardDto(
            String front_text,
            String back_text
    ) {}

    public record QuizDto(
            String content,
            List<String> options,
            String correct_answer
    ) {}
}
