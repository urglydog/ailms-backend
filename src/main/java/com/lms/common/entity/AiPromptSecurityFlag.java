package com.lms.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Lớp 2b chống Prompt Injection/DoW (UpComming_Plan.md, phân tích 25/09/2026) — AI Worker ghi
 * vào bảng này khi 1 câu hỏi gửi tới AI Discovery/Instructor AI Assistant khớp pattern nghi vấn
 * (xem {@code ai-worker/app/services/security_heuristics.py}). CHỈ ghi log để Admin xem lại,
 * KHÔNG dùng để chặn câu trả lời — tránh false positive làm gián đoạn người dùng thật.
 */
@Entity
@Table(name = "ai_prompt_security_flags", indexes = {
        @Index(name = "idx_ai_prompt_security_flags_created_at", columnList = "created_at")
})
@Getter
@Setter
public class AiPromptSecurityFlag extends BaseEntity {

    @Column(name = "user_email", length = 255)
    private String userEmail;

    /** DISCOVERY hoặc INSTRUCTOR_AI. */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "matched_pattern", nullable = false, length = 100)
    private String matchedPattern;

    @Column(name = "message_snapshot", nullable = false, columnDefinition = "TEXT")
    private String messageSnapshot;
}
