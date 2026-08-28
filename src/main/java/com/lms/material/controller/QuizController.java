package com.lms.material.controller;

import com.lms.material.dto.QuizAttemptDto;
import com.lms.material.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PutMapping("/instructor/quizzes/{quizId}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> setOfficial(Principal principal, @PathVariable Long quizId) {
        quizService.setOfficial(principal.getName(), quizId);
        return ResponseEntity.ok().build();
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
}
