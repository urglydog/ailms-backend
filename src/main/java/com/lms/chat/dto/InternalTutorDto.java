package com.lms.chat.dto;

/**
 * DTO cho {@code /api/internal/tutor/**} — hợp đồng HTTP giữa AI Worker (Python) và
 * backend cho UC30. AI Worker chỉ ĐỌC ngữ cảnh qua đây, không ghi gì — {@code be/} tự
 * lưu {@code ChatMessage} sau khi nhận câu trả lời đồng bộ (xem {@code TutorService}).
 */
public class InternalTutorDto {

    public record ContextRes(
            String lessonTitle,
            String sourceLanguage,
            Integer durationSec
    ) {}
}
