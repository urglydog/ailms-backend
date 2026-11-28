package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cache câu hỏi gợi ý do AI sinh cho 1 bài học (26/11/2026, mở rộng ngoài đặc tả gốc).
 * Trạng thái: PENDING (chờ job nền) | READY | FAILED. Xem {@code TutorAiSuggestionService}.
 */
@Entity
@Table(name = "lesson_ai_suggestions")
@Getter
@Setter
public class LessonAiSuggestion extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    @Column(name = "lesson_id", nullable = false, unique = true)
    private Long lessonId;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "status", nullable = false, length = 16)
    private String status = PENDING;

    /** Mảng JSON các câu hỏi (chuỗi). */
    @Column(name = "questions", columnDefinition = "TEXT")
    private String questions;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;
}
