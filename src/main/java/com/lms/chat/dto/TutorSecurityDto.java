package com.lms.chat.dto;

import java.time.LocalDateTime;

/** DTO cho {@code /api/v1/admin/tutor-security/**} (BR-TUTOR-SEC-06). */
public class TutorSecurityDto {

    /** 1 dòng trong danh sách flag cho Admin xem lại. */
    public record FlagRes(
            Long id,
            Long studentId,
            String studentName,
            String studentEmail,
            Long courseId,
            String courseTitle,
            String matchedPattern,
            String messageSnapshot,
            LocalDateTime createdAt
    ) {}
}
