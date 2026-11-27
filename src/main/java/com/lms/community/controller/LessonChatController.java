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

    /** Câu trả lời hay nhất (27/11/2026) — người hỏi hoặc giảng viên khoá; body {@code {"answerId": null}} = bỏ đánh dấu. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{questionId}/best-answer")
    public org.springframework.http.ResponseEntity<Void> setBestAnswer(
            java.security.Principal principal, @PathVariable Long lessonId, @PathVariable String questionId,
            @RequestBody com.lms.community.dto.LessonQaDto.BestAnswerReq req) {
        chatService.setBestAnswer(principal.getName(), questionId, req.answerId());
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
