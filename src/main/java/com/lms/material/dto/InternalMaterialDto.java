package com.lms.material.dto;

import com.lms.dubbing.dto.InternalDubbingDto.SegmentDto;
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
            /** Theo TỪNG bài trong phạm vi — BR-MAT-01: dịch trực tiếp từ transcript gốc, không
             * phụ thuộc bài đã lồng tiếng ngôn ngữ đích hay chưa. Nếu ngôn ngữ đích ĐÃ có bản dịch
             * sẵn (do lồng tiếng hoặc lần sinh học liệu trước đó), dùng thẳng, khỏi dịch lại. */
            List<LessonContextDto> lessons
    ) {}

    @Builder
    public record LessonContextDto(
            Long lessonId,
            /** Ngôn ngữ gốc thật của bài này (Lesson.sourceLanguage) — cần truyền đúng cho Gemini
             * dịch (`translation.translate_batch`), khác `language` (ngôn ngữ ĐÍCH của cả yêu cầu). */
            String sourceLanguage,
            boolean targetTranscriptAvailable,
            /** Chỉ có giá trị khi {@code !targetTranscriptAvailable} — AI Worker tự dịch rồi báo
             * lại qua {@code InternalMaterialTranscriptController} để lưu tái sử dụng sau này. */
            List<SegmentDto> sourceSegments,
            /** Chỉ có giá trị khi {@code targetTranscriptAvailable}. */
            List<SegmentDto> targetSegments
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

    /** Báo bản dịch (do sinh học liệu tự dịch) để lưu tái sử dụng — xem
     * {@code InternalMaterialTranscriptController}. */
    public record SaveTranslatedSegmentsReq(
            String language,
            List<SegmentDto> segments
    ) {}
}
