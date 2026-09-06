package com.lms.chat.controller;

import com.lms.chat.dto.InternalTutorDto.ContextRes;
import com.lms.chat.dto.InternalTutorDto.CourseLessonRes;
import com.lms.chat.service.InternalTutorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UC30 — callback nội bộ cho AI Worker (Python). Xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter}, KHÔNG phải JWT — không đặt
 * {@code @PreAuthorize} (giống {@link com.lms.dubbing.controller.InternalDubbingController}).
 */
@RestController
@RequestMapping("/api/internal/tutor")
@RequiredArgsConstructor
public class InternalTutorController {

    private final InternalTutorService internalTutorService;

    @GetMapping("/lessons/{lessonId}/context")
    public ResponseEntity<ContextRes> getContext(@PathVariable Long lessonId) {
        return ResponseEntity.ok(internalTutorService.getContext(lessonId));
    }

    /** UC30 mở rộng (06/09/2026) — danh sách bài học của khóa, để AI Worker tự phân loại bài
     * học viên nhắc tới trong câu hỏi (nếu có) khác bài đang mở. */
    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<CourseLessonRes>> getCourseLessons(@PathVariable Long courseId) {
        return ResponseEntity.ok(internalTutorService.getCourseLessons(courseId));
    }
}
