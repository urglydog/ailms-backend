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
            LocalDateTime createdAt,
            String courseTitle,
            String requestedByName,
            String requestedByEmail,
            LocalDateTime finishedAt,
            /** Job lỗi nhưng bài học + ngôn ngữ này đã có bản lồng tiếng thành công (nên không còn cần xử lý). */
            boolean superseded,
            /** Được phép thử lại: đang lỗi, còn lượt thử và chưa có bản thành công. */
            boolean retryable
    ) {}
}
