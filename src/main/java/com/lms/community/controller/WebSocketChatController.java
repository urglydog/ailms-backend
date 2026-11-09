package com.lms.community.controller;

import com.lms.community.dto.ChatMessageDto;
import com.lms.community.service.LessonChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final LessonChatService chatService;

    /** (20/09/2026, sửa lỗi) — broadcast lại DTO đã LƯU (từ {@code saveMessage}), không còn echo
     * nguyên văn payload thô client gửi lên — id/timestamp/isInstructor phải là dữ liệu THẬT từ
     * DB, xem docblock {@link ChatMessageDto}. */
    @MessageMapping("/chat/{lessonId}")
    @SendTo("/topic/lesson/{lessonId}/chat")
    public ChatMessageDto handleChatMessage(@DestinationVariable Long lessonId, ChatMessageDto message) {
        Long userId = Long.parseLong(message.senderId());
        return chatService.saveMessage(lessonId, userId, message.senderName(), message.content(), message.parentId());
    }
}
