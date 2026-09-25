package com.lms.material.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class QuizAttemptDto {

    public record OptionDto(
            Long id,
            String content
    ) {}

    public record QuestionDto(
            Long id,
            String content,
            Integer displayOrder,
            Boolean isMultipleChoice,
            List<OptionDto> options
    ) {}

    public record StartRes(
            Long attemptId,
            Long quizId,
            List<QuestionDto> questions,
            Boolean isProctored,
            Integer maxViolations,
            Integer durationMinutes,
            LocalDateTime startedAt
    ) {}


    public record SubmitReq(
            // Key: quizQuestionId, Value: list of selectedOptionIds
            Map<Long, List<Long>> answers
    ) {}

    public record AnswerDetailDto(
            Long questionId,
            String content,
            List<Long> selectedOptionIds,
            List<Long> correctOptionIds,
            Boolean isCorrect,
            List<OptionDto> options
    ) {}

    public record SubmitRes(
            Long attemptId,
            BigDecimal score,
            Integer correctCount,
            Integer totalQuestions,
            List<AnswerDetailDto> details,
            Boolean isArchived,
            String aiRiskLevel,
            String aiRiskExplanation
    ) {}

    /** UC-ANTICHEAT — request FE gửi khi bắt được 1 vi phạm (tab-switch, copy/paste, DevTools,
     * idle, âm thanh, Gemini Vision flag...). */
    public record ViolationReq(
            String type,
            String detail
    ) {}

    public record ViolationRes(
            Integer violationCount,
            Integer maxViolations,
            Boolean shouldAutoSubmit
    ) {}

    /** Request chụp khung hình webcam định kỳ để AI-worker xác minh bằng Gemini Vision. */
    public record ProctorFrameReq(
            String imageBase64,
            String mimeType
    ) {}

    public record ProctorFrameRes(
            Integer personCount,
            String gazeDirection,
            Boolean flagged,
            Integer violationCount,
            Integer maxViolations,
            Boolean shouldAutoSubmit
    ) {}

    public record ExplainReq(
            Long questionId,
            Long selectedOptionId
    ) {}

    public record ExplainRes(
            String explanation
    ) {}

    public record HistoryRes(
            Long id,
            BigDecimal score,
            Integer correctCount,
            Integer totalQuestions,
            LocalDateTime submittedAt,
            Long quizId,
            String status,
            Boolean isArchived,
            Boolean allowReview
    ) {}
}
