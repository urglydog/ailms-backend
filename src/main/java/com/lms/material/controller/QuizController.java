package com.lms.material.controller;

import com.lms.material.dto.QuizAttemptDto;
import com.lms.material.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PutMapping("/instructor/quizzes/{quizId}/settings")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> updateQuizSettings(
            Principal principal, 
            @PathVariable Long quizId, 
            @RequestBody com.lms.material.dto.QuizDto.QuizSettingsReq req) {
        quizService.updateQuizSettings(principal.getName(), quizId, req);
        return ResponseEntity.ok(Map.of("message", "Lưu cấu hình thành công"));
    }

    @PutMapping("/instructor/quizzes/questions/{questionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> updateQuestion(
            Principal principal,
            @PathVariable Long questionId,
            @RequestBody com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        quizService.updateQuestion(principal.getName(), questionId, req);
        return ResponseEntity.ok(Map.of("message", "Cập nhật câu hỏi thành công"));
    }

    @PostMapping("/instructor/quizzes/{quizId}/questions")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> addQuestion(
            Principal principal,
            @PathVariable Long quizId,
            @RequestBody com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        quizService.addQuestion(principal.getName(), quizId, req);
        return ResponseEntity.ok(Map.of("message", "Thêm câu hỏi thành công"));
    }

    @DeleteMapping("/instructor/quizzes/questions/{questionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> deleteQuestion(
            Principal principal,
            @PathVariable Long questionId) {
        quizService.deleteQuestion(principal.getName(), questionId);
        return ResponseEntity.ok(Map.of("message", "Xóa câu hỏi thành công"));
    }
    @PutMapping("/quizzes/questions/{questionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> updatePersonalQuestion(
            Principal principal,
            @PathVariable Long questionId,
            @RequestBody com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        quizService.updatePersonalQuestion(principal.getName(), questionId, req);
        return ResponseEntity.ok(Map.of("message", "Cập nhật câu hỏi thành công"));
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> addPersonalQuestion(
            Principal principal,
            @PathVariable Long quizId,
            @RequestBody com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        quizService.addPersonalQuestion(principal.getName(), quizId, req);
        return ResponseEntity.ok(Map.of("message", "Thêm câu hỏi thành công"));
    }

    @DeleteMapping("/quizzes/questions/{questionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> deletePersonalQuestion(
            Principal principal,
            @PathVariable Long questionId) {
        quizService.deletePersonalQuestion(principal.getName(), questionId);
        return ResponseEntity.ok(Map.of("message", "Xóa câu hỏi thành công"));
    }


    // (20/09/2026, sửa lỗi) — 4 endpoint làm bài quiz + gia sư giải thích đáp án sai bên dưới
    // đổi từ `hasRole('STUDENT')` sang `hasAnyRole`: tài khoản đã lên Giảng viên vẫn cần làm
    // được quiz của khóa đã mua từ lúc còn là Học viên (xem docblock EnrollmentController.getMine).

    @GetMapping("/quizzes/{quizId}/start-attempt")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<QuizAttemptDto.StartRes> startAttempt(Principal principal, @PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.startAttempt(principal.getName(), quizId));
    }

    @PostMapping("/quizzes/attempts/{attemptId}/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<QuizAttemptDto.SubmitRes> submitAttempt(
            Principal principal,
            @PathVariable Long attemptId,
            @RequestBody QuizAttemptDto.SubmitReq req) {
        return ResponseEntity.ok(quizService.submitAttempt(principal.getName(), attemptId, req));
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<QuizAttemptDto.HistoryRes>> getAttemptHistory(Principal principal, @PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.getAttemptHistory(principal.getName(), quizId));
    }

    @GetMapping("/quizzes/attempts/{attemptId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<QuizAttemptDto.SubmitRes> getAttemptDetail(Principal principal, @PathVariable Long attemptId) {
        return ResponseEntity.ok(quizService.getAttemptDetail(principal.getName(), attemptId));
    }

    @PostMapping("/quizzes/tutor/explain")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<QuizAttemptDto.ExplainRes> explainWrongAnswer(
            Principal principal,
            @RequestBody QuizAttemptDto.ExplainReq req) {
        return ResponseEntity.ok(quizService.explainWrongAnswer(principal.getName(), req));
    }
}
