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

    @MessageMapping("/chat/{lessonId}")
    @SendTo("/topic/lesson/{lessonId}/chat")
    public ChatMessageDto handleChatMessage(@DestinationVariable Long lessonId, ChatMessageDto message) {
        try {
            Long userId = Long.parseLong(message.id());
            chatService.saveMessage(lessonId, userId, message.senderName(), message.content());
        } catch (Exception e) {
            // Ignore exception for demo if user is invalid, but still broadcast
        }
        return message;
    }
}
