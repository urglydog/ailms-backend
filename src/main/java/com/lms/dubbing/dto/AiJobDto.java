package com.lms.dubbing.dto;

import java.time.LocalDateTime;

/** UC45 — Admin giám sát hàng đợi lồng tiếng. */
public class AiJobDto {

    public record Res(
            Long id,
            Long lessonId,
            String lessonTitle,
            String targetLanguage,
            String status,
            Integer totalChunks,
            Integer doneChunks,
            int progressPercent,
            Integer retryCount,
            String errorMessage,
            LocalDateTime createdAt
    ) {}
}
