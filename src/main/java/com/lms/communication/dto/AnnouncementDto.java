package com.lms.communication.dto;

import java.time.LocalDateTime;

public class AnnouncementDto {

    public record CreateReq(Long courseId, String title, String content) {
    }

    public record Res(
            Long id,
            Long courseId,
            String courseTitle,
            String title,
            String content,
            LocalDateTime createdAt
    ) {
    }
}
