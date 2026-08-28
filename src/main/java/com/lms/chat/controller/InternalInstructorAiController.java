package com.lms.chat.controller;

import com.lms.chat.service.InternalInstructorAiService;
import com.lms.enrollment.dto.CourseReviewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller nội bộ phục vụ AI Worker cho tính năng Instructor AI Assistant.
 * Không bị chặn bởi JWT filter nhờ đường dẫn /api/internal/**.
 */
@RestController
@RequestMapping("/api/internal/instructor-ai")
@RequiredArgsConstructor
public class InternalInstructorAiController {

    private final InternalInstructorAiService internalInstructorAiService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@RequestParam String email) {
        return ResponseEntity.ok(internalInstructorAiService.getInstructorDashboard(email));
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ResponseEntity<Page<CourseReviewDto.Res>> listForCourse(
            @PathVariable Long courseId,
            @RequestParam String email,
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(internalInstructorAiService.listForCourse(email, courseId, pageable));
    }
}
