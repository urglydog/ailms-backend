package com.lms.community.controller;

import com.lms.community.dto.ChatMessageDto;
import com.lms.community.service.LessonChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lessons/{lessonId}/chats")
@RequiredArgsConstructor
public class LessonChatController {

    private final LessonChatService chatService;

    @GetMapping
    public List<ChatMessageDto> getChatHistory(@PathVariable Long lessonId) {
        return chatService.getChatHistory(lessonId);
    }
}
