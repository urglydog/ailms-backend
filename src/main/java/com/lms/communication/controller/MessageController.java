package com.lms.communication.controller;

import com.lms.communication.dto.MessageDto.ConversationRes;
import com.lms.communication.dto.MessageDto.MessageRes;
import com.lms.communication.dto.MessageDto.SendReq;
import com.lms.communication.dto.MessageDto.StartReq;
import com.lms.communication.service.MessageService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationRes>> listConversations(Principal principal) {
        return ResponseEntity.ok(messageService.listConversations(principal.getName()));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<List<MessageRes>> getMessages(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(messageService.getMessages(principal.getName(), id));
    }

    @PostMapping("/conversations/{id}")
    public ResponseEntity<MessageRes> sendMessage(
            Principal principal, @PathVariable Long id, @RequestBody SendReq req) {
        return ResponseEntity.ok(messageService.sendMessage(principal.getName(), id, req.content()));
    }

    /** Học viên bắt đầu hội thoại với giảng viên của 1 khóa mình đã ghi danh. */
    @PostMapping("/conversations/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ConversationRes> startFromStudent(Principal principal, @RequestBody StartReq req) {
        return ResponseEntity.ok(messageService.startFromStudent(principal.getName(), req));
    }

    /** Giảng viên bắt đầu hội thoại với 1 học viên (nút "Compose"). */
    @PostMapping("/conversations/start-as-instructor")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ConversationRes> startFromInstructor(Principal principal, @RequestBody StartReq req) {
        return ResponseEntity.ok(messageService.startFromInstructor(principal.getName(), req));
    }
}
