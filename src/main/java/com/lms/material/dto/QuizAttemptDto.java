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
            List<QuestionDto> questions
    ) {}

    public record SubmitReq(
            // Key: quizQuestionId, Value: selectedOptionId
            Map<Long, Long> answers
    ) {}

    public record SubmitRes(
            Long attemptId,
            BigDecimal score,
            Integer correctCount,
            Integer totalQuestions
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
