package com.lms.enrollment.controller;

import com.lms.enrollment.dto.LessonPlayerDto.Res;
import com.lms.enrollment.service.LessonPlayerService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC16/17 — phát bài học cho người dùng ĐÃ đăng nhập. Khác {@code /api/v1/courses/lessons/{id}/player}
 * (UC11, nằm trong {@code PUBLIC_GET_ENDPOINTS}): route này không public, chỉ cần JWT hợp lệ
 * (không giới hạn role — quyền truy cập THẬT được kiểm trong {@code EnrollmentSecurity}).
 */
@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
public class LessonPlayerController {

    private final LessonPlayerService lessonPlayerService;

    @GetMapping("/{lessonId}/player")
    public ResponseEntity<Res> getForPlayback(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonPlayerService.getLessonForPlayback(principal.getName(), lessonId));
    }
}
