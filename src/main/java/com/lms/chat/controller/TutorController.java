package com.lms.chat.controller;

import com.lms.chat.dto.TutorDto.AskReq;
import com.lms.chat.dto.TutorDto.AskRes;
import com.lms.chat.service.TutorService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC30 — hỏi đáp Gia sư AI Socratic. */
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
}
