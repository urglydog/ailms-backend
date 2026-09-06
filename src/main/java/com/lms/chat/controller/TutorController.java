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

/** UC30 — hỏi đáp Gia sư AI Socratic + UC30 mở rộng (lịch sử trò chuyện kiểu ChatGPT).
 *
 * <p>06/09/2026 — route chuyển từ {@code /lessons/{lessonId}/tutor/**} sang
 * {@code /courses/{courseId}/tutor/**}: phiên chat (danh sách lịch sử) giờ dùng CHUNG cho mọi
 * bài học trong 1 khóa, xem {@link TutorService}. Bài học đang mở truyền riêng trong
 * {@link AskReq#currentLessonId()} của từng lượt hỏi, không còn cố định theo route nữa. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class TutorController {

    private final TutorService tutorService;

    @PostMapping("/api/v1/courses/{courseId}/tutor/ask")
    public ResponseEntity<AskRes> ask(
            Principal principal, @PathVariable Long courseId, @Valid @RequestBody AskReq req) {
        return ResponseEntity.ok(tutorService.ask(principal.getName(), courseId, req));
    }

    /** Danh sách các cuộc trò chuyện đã có với khóa học này (mọi bài học), mới nhất trước. */
    @GetMapping("/api/v1/courses/{courseId}/tutor/sessions")
    public ResponseEntity<List<SessionRes>> listSessions(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.ok(tutorService.listSessions(principal.getName(), courseId));
    }

    /** Phục hồi lại toàn bộ tin nhắn của 1 cuộc trò chuyện cũ. */
    @GetMapping("/api/v1/courses/{courseId}/tutor/sessions/{sessionId}/messages")
    public ResponseEntity<List<MessageRes>> getMessages(
            Principal principal, @PathVariable Long courseId, @PathVariable Long sessionId) {
        return ResponseEntity.ok(tutorService.getMessages(principal.getName(), courseId, sessionId));
    }

    /** Bắt đầu 1 cuộc trò chuyện mới (khác hẳn phiên gần nhất) cho khóa học này. */
    @PostMapping("/api/v1/courses/{courseId}/tutor/sessions")
    public ResponseEntity<SessionRes> startNewSession(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.ok(tutorService.startNewSession(principal.getName(), courseId));
    }

    /** Đổi tên thủ công 1 cuộc trò chuyện. */
    @PatchMapping("/api/v1/courses/{courseId}/tutor/sessions/{sessionId}")
    public ResponseEntity<Void> rename(
            Principal principal, @PathVariable Long courseId, @PathVariable Long sessionId,
            @Valid @RequestBody RenameSessionReq req) {
        tutorService.renameSession(principal.getName(), courseId, sessionId, req.title());
        return ResponseEntity.noContent().build();
    }

    /** Ghim/bỏ ghim 1 cuộc trò chuyện lên đầu danh sách lịch sử. */
    @PatchMapping("/api/v1/courses/{courseId}/tutor/sessions/{sessionId}/pin")
    public ResponseEntity<Void> pin(
            Principal principal, @PathVariable Long courseId, @PathVariable Long sessionId,
            @Valid @RequestBody PinSessionReq req) {
        tutorService.pinSession(principal.getName(), courseId, sessionId, req.pinned());
        return ResponseEntity.noContent().build();
    }

    /** Xoá hẳn 1 cuộc trò chuyện. */
    @DeleteMapping("/api/v1/courses/{courseId}/tutor/sessions/{sessionId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long courseId, @PathVariable Long sessionId) {
        tutorService.deleteSession(principal.getName(), courseId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
