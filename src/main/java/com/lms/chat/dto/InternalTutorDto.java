package com.lms.chat.dto;

import java.util.List;

/**
 * DTO cho {@code /api/internal/tutor/**} — hợp đồng HTTP giữa AI Worker (Python) và
 * backend cho UC30. AI Worker chỉ ĐỌC ngữ cảnh qua đây, không ghi gì — {@code be/} tự
 * lưu {@code ChatMessage} sau khi nhận câu trả lời đồng bộ (xem {@code TutorService}).
 */
public class InternalTutorDto {

    public record ContextRes(
            String lessonTitle,
            String sourceLanguage,
            Integer durationSec,
            /** UC30 mở rộng — ngữ cảnh cho system prompt fallback Google Search Grounding
             * khi transcript không có đoạn nào đủ liên quan (xem TutorService bên Python). */
            String courseTitle,
            String courseDescription
    ) {}

    /** UC30 mở rộng (06/09/2026) — 1 dòng trong danh sách bài học của khóa, cho AI Worker tự
     * phân loại bài học học viên NHẮC TỚI trong câu hỏi (nếu có) khác bài đang mở, xem
     * {@code app/services/tutor_service.py::resolve_target_lesson}. */
    public record CourseLessonRes(
            Long lessonId,
            String lessonTitle,
            Integer displayOrder
    ) {}
}
