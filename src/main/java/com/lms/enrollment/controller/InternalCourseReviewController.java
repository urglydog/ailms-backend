package com.lms.enrollment.controller;

import com.lms.enrollment.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/course-reviews")
@RequiredArgsConstructor
public class InternalCourseReviewController {

    private final CourseReviewService courseReviewService;

    public record HideReq(String reason) {}

    @PutMapping("/{id}/hide")
    public ResponseEntity<Void> hideByAi(@PathVariable Long id, @RequestBody HideReq req) {
        courseReviewService.hideByAi(id, req.reason());
        return ResponseEntity.ok().build();
    }
}
