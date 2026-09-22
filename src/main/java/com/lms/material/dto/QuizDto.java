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

    public record OptionReq(
            Long id,
            String content,
            Boolean isCorrect
    ) {}

    public record QuestionUpdateReq(
            String content,
            Boolean isMultipleChoice,
            java.util.List<OptionReq> options
    ) {}

    /** Task 4 — kết quả import CSV hàng loạt: số câu thêm thành công + lỗi theo từng dòng (1-based, tính cả header). */
    public record ImportResultRes(
            int importedCount,
            java.util.List<String> errors
    ) {}

}
