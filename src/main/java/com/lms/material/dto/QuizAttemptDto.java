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
            List<OptionDto> options
    ) {}

    public record StartRes(
            Long attemptId,
            Long quizId,
            List<QuestionDto> questions,
            Boolean isProctored,
            Integer maxViolations,
            Integer durationMinutes
    ) {}


    public record SubmitReq(
            // Key: quizQuestionId, Value: selectedOptionId
            Map<Long, Long> answers
    ) {}

    public record AnswerDetailDto(
            Long questionId,
            String content,
            Long selectedOptionId,
            Long correctOptionId,
            boolean isCorrect,
            List<OptionDto> options
    ) {}

    public record SubmitRes(
            Long attemptId,
            BigDecimal score,
            Integer correctCount,
            Integer totalQuestions,
            List<AnswerDetailDto> details
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
            Long quizId
    ) {}
}
