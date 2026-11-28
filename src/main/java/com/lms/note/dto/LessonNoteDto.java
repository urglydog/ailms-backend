package com.lms.note.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** DTO cho ghi chú gắn mốc thời gian (26/11/2026). */
public class LessonNoteDto {

    public static final int MAX_CONTENT = 2000;

    public record NoteRes(
            Long id,
            Long lessonId,
            String lessonTitle,
            Long courseId,
            Integer timestampSec,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record CreateReq(
            @NotNull @Min(0) @Max(86_400) Integer timestampSec,
            @NotBlank @Size(max = MAX_CONTENT) String content) {}

    public record UpdateReq(
            @Min(0) @Max(86_400) Integer timestampSec,
            @NotBlank @Size(max = MAX_CONTENT) String content) {}
}
