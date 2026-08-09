package com.lms.enrollment.controller;

import com.lms.enrollment.dto.EnrollmentDto.Res;
import com.lms.enrollment.service.EnrollmentService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Khóa học của tôi" — không nằm trong {@code PUBLIC_GET_ENDPOINTS} nên mặc định cần đăng nhập. */
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Res>> getMine(Principal principal) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(principal.getName()));
    }

    @PostMapping("/free/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> enrollFreeCourse(Principal principal, @org.springframework.web.bind.annotation.PathVariable Long courseId) {
        enrollmentService.enrollFreeCourse(principal.getName(), courseId);
        return ResponseEntity.ok().build();
    }
}
