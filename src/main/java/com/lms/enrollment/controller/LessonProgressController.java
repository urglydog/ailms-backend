package com.lms.enrollment.controller;

import com.lms.enrollment.dto.LessonProgressDto.RecordReq;
import com.lms.enrollment.dto.LessonProgressDto.Res;
import com.lms.enrollment.service.LessonProgressService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC21 — ghi nhận tiến độ xem bài học, gọi định kỳ mỗi 15s + tại pause/seek/unload (BR-PROGRESS-03). */
@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
public class LessonProgressController {

    private final LessonProgressService lessonProgressService;

    @PostMapping("/{lessonId}/progress")
    public ResponseEntity<Res> recordProgress(
            Principal principal, @PathVariable Long lessonId, @Valid @RequestBody RecordReq req) {
        return ResponseEntity.ok(lessonProgressService.recordProgress(principal.getName(), lessonId, req));
    }
}
