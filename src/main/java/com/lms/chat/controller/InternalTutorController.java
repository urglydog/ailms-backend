package com.lms.chat.controller;

import com.lms.chat.dto.InternalTutorDto.ContextRes;
import com.lms.chat.service.InternalTutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UC30 — callback nội bộ cho AI Worker (Python). Xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter}, KHÔNG phải JWT — không đặt
 * {@code @PreAuthorize} (giống {@link com.lms.dubbing.controller.InternalDubbingController}).
 */
@RestController
@RequestMapping("/api/internal/tutor/lessons/{lessonId}")
@RequiredArgsConstructor
public class InternalTutorController {

    private final InternalTutorService internalTutorService;

    @GetMapping("/context")
    public ResponseEntity<ContextRes> getContext(@PathVariable Long lessonId) {
        return ResponseEntity.ok(internalTutorService.getContext(lessonId));
    }
}
