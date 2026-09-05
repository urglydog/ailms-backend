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

    @PutMapping("/instructor/quizzes/{quizId}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> setOfficial(Principal principal, @PathVariable Long quizId) {
        quizService.setOfficial(principal.getName(), quizId);
        return ResponseEntity.ok(Map.of("message", "Đã đánh dấu là học liệu chính thức"));
    }

    @PutMapping("/instructor/quizzes/{quizId}/settings")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> updateQuizSettings(
            Principal principal, 
            @PathVariable Long quizId, 
            @RequestBody com.lms.material.dto.QuizDto.QuizSettingsReq req) {
        quizService.updateQuizSettings(principal.getName(), quizId, req);
        return ResponseEntity.ok(Map.of("message", "Lưu cấu hình thành công"));
    }

    @PutMapping("/instructor/quizzes/questions/{questionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> updateQuestion(
            Principal principal,
            @PathVariable Long questionId,
            @RequestBody com.lms.material.dto.QuizDto.QuestionUpdateReq req) {
        quizService.updateQuestion(principal.getName(), questionId, req);
        return ResponseEntity.ok(Map.of("message", "Cập nhật câu hỏi thành công"));
    }

    @DeleteMapping("/instructor/quizzes/questions/{questionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Map<String, String>> deleteQuestion(
            Principal principal,
            @PathVariable Long questionId) {
        quizService.deleteQuestion(principal.getName(), questionId);
        return ResponseEntity.ok(Map.of("message", "Xóa câu hỏi thành công"));
    }

    @GetMapping("/courses/{courseId}/quizzes/official/attempt")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QuizAttemptDto.StartRes> startAttempt(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.ok(quizService.startAttempt(principal.getName(), courseId));
    }

    @PostMapping("/quizzes/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QuizAttemptDto.SubmitRes> submitAttempt(
            Principal principal,
            @PathVariable Long attemptId,
            @RequestBody QuizAttemptDto.SubmitReq req) {
        return ResponseEntity.ok(quizService.submitAttempt(principal.getName(), attemptId, req));
    }

    @GetMapping("/courses/{courseId}/quizzes/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<QuizAttemptDto.HistoryRes>> getAttemptHistory(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.ok(quizService.getAttemptHistory(principal.getName(), courseId));
    }

    @PostMapping("/quizzes/tutor/explain")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QuizAttemptDto.ExplainRes> explainWrongAnswer(
            Principal principal,
            @RequestBody QuizAttemptDto.ExplainReq req) {
        return ResponseEntity.ok(quizService.explainWrongAnswer(principal.getName(), req));
    }
}
