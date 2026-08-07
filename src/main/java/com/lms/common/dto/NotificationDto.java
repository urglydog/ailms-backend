package com.lms.common.dto;

import java.time.LocalDateTime;

public class NotificationDto {
    public record NotificationRes(
            Long id,
            String type,
            String title,
            String content,
            String linkUrl,
            boolean isRead,
            LocalDateTime createdAt
    ) {}
}
