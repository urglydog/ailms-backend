package com.lms.chat.controller;

import com.lms.chat.dto.TutorDto.AskReq;
import com.lms.chat.dto.TutorDto.AskRes;
import com.lms.chat.dto.TutorDto.MessageRes;
import com.lms.chat.dto.TutorDto.PinSessionReq;
import com.lms.chat.dto.TutorDto.RenameSessionReq;
import com.lms.chat.dto.TutorDto.SessionRes;
import com.lms.chat.service.TutorService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC30 — hỏi đáp Gia sư AI Socratic + UC30 mở rộng (lịch sử trò chuyện kiểu ChatGPT). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class TutorController {

    private final TutorService tutorService;

    @PostMapping("/api/v1/lessons/{lessonId}/tutor/ask")
    public ResponseEntity<AskRes> ask(
            Principal principal, @PathVariable Long lessonId, @Valid @RequestBody AskReq req) {
        return ResponseEntity.ok(tutorService.ask(principal.getName(), lessonId, req));
    }

    /** Danh sách các cuộc trò chuyện đã có với bài học này, mới nhất trước. */
    @GetMapping("/api/v1/lessons/{lessonId}/tutor/sessions")
    public ResponseEntity<List<SessionRes>> listSessions(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(tutorService.listSessions(principal.getName(), lessonId));
    }

    /** Phục hồi lại toàn bộ tin nhắn của 1 cuộc trò chuyện cũ. */
    @GetMapping("/api/v1/lessons/{lessonId}/tutor/sessions/{sessionId}/messages")
    public ResponseEntity<List<MessageRes>> getMessages(
            Principal principal, @PathVariable Long lessonId, @PathVariable Long sessionId) {
        return ResponseEntity.ok(tutorService.getMessages(principal.getName(), lessonId, sessionId));
    }

    /** Bắt đầu 1 cuộc trò chuyện mới (khác hẳn phiên gần nhất) cho bài học này. */
    @PostMapping("/api/v1/lessons/{lessonId}/tutor/sessions")
    public ResponseEntity<SessionRes> startNewSession(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(tutorService.startNewSession(principal.getName(), lessonId));
    }

    /** Đổi tên thủ công 1 cuộc trò chuyện. */
    @PatchMapping("/api/v1/lessons/{lessonId}/tutor/sessions/{sessionId}")
    public ResponseEntity<Void> rename(
            Principal principal, @PathVariable Long lessonId, @PathVariable Long sessionId,
            @Valid @RequestBody RenameSessionReq req) {
        tutorService.renameSession(principal.getName(), lessonId, sessionId, req.title());
        return ResponseEntity.noContent().build();
    }

    /** Ghim/bỏ ghim 1 cuộc trò chuyện lên đầu danh sách lịch sử. */
    @PatchMapping("/api/v1/lessons/{lessonId}/tutor/sessions/{sessionId}/pin")
    public ResponseEntity<Void> pin(
            Principal principal, @PathVariable Long lessonId, @PathVariable Long sessionId,
            @Valid @RequestBody PinSessionReq req) {
        tutorService.pinSession(principal.getName(), lessonId, sessionId, req.pinned());
        return ResponseEntity.noContent().build();
    }

    /** Xoá hẳn 1 cuộc trò chuyện. */
    @DeleteMapping("/api/v1/lessons/{lessonId}/tutor/sessions/{sessionId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long lessonId, @PathVariable Long sessionId) {
        tutorService.deleteSession(principal.getName(), lessonId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
