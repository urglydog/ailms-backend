package com.lms.community.controller;

import com.lms.community.dto.ChatMessageDto;
import com.lms.community.service.LessonChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lessons/{lessonId}/chats")
@RequiredArgsConstructor
public class LessonChatController {

    private final LessonChatService chatService;

    /** Lịch sử chat chỉ cho người đã đăng nhập (26/11/2026): trước đây `/api/v1/lessons/**` công khai nên ai cũng đọc được tên + id + nội dung. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<ChatMessageDto> getChatHistory(@PathVariable Long lessonId) {
        return chatService.getChatHistory(lessonId);
    }
}
