package com.lms.community.dto;

public record ChatMessageDto(
        String id, // có thể dùng làm userId ở payload gửi lên
        String senderName,
        String content,
        String timestamp,
        String parentId
) {
}
