package com.lms.communication.dto;

import java.time.LocalDateTime;

public class MessageDto {

    public record StartReq(Long courseId, Long studentId) {
    }

    public record SendReq(String content) {
    }

    public record ConversationRes(
            Long id,
            Long otherUserId,
            String otherUserName,
            String otherUserAvatarUrl,
            Long courseId,
            String courseTitle,
            String lastMessagePreview,
            LocalDateTime lastMessageAt,
            long unreadCount
    ) {
    }

    public record MessageRes(
            Long id,
            Long senderId,
            String senderName,
            String content,
            LocalDateTime createdAt,
            boolean mine
    ) {
    }
}
