package com.lms.community.controller;

import com.lms.community.dto.LessonQaDto.QuestionRes;
import com.lms.community.dto.LessonQaDto.ReplyReq;
import com.lms.community.dto.LessonQaDto.ThreadRes;
import com.lms.community.service.LessonChatService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** "Giao tiếp > Hỏi đáp" của Giảng viên (19/09/2026) — hộp thư gộp câu hỏi từ mọi khóa học,
 * xem {@link LessonChatService}. */
@RestController
@RequestMapping("/api/v1/instructor/qa")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorQaController {

    private final LessonChatService lessonChatService;

    @GetMapping("/questions")
    public ResponseEntity<Page<QuestionRes>> listQuestions(
            Principal principal,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "false") boolean onlyUnanswered,
            @RequestParam(defaultValue = "false") boolean noInstructorAnswer,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "false") boolean noBestAnswer,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        return ResponseEntity.ok(lessonChatService.listQuestionsForInstructor(principal.getName(),
                new LessonChatService.QaFilter(courseId, onlyUnanswered, noInstructorAnswer, unread, noBestAnswer, search, sort),
                pageable));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<ThreadRes> getThread(Principal principal, @PathVariable String questionId) {
        return ResponseEntity.ok(lessonChatService.getThread(principal.getName(), questionId));
    }

    /** Đánh dấu đã đọc / chưa đọc (27/11/2026). Mở câu hỏi đã tự đánh dấu đã đọc. */
    @PutMapping("/questions/{questionId}/read")
    public ResponseEntity<Void> setRead(Principal principal, @PathVariable String questionId,
                                        @RequestParam(defaultValue = "true") boolean read) {
        lessonChatService.setRead(principal.getName(), questionId, read);
        return ResponseEntity.noContent().build();
    }

    /** Đặt / bỏ câu trả lời hay nhất (27/11/2026); body {@code {"answerId": null}} = bỏ. */
    @PutMapping("/questions/{questionId}/best-answer")
    public ResponseEntity<Void> setBestAnswer(Principal principal, @PathVariable String questionId,
                                              @RequestBody com.lms.community.dto.LessonQaDto.BestAnswerReq req) {
        lessonChatService.setBestAnswer(principal.getName(), questionId, req.answerId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/questions/{questionId}/reply")
    public ResponseEntity<Void> reply(
            Principal principal, @PathVariable String questionId, @RequestBody ReplyReq req) {
        lessonChatService.postInstructorReply(principal.getName(), questionId, req.content());
        return ResponseEntity.ok().build();
    }
}
