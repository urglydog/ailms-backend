package com.lms.enrollment.controller;

import com.lms.enrollment.dto.CourseReviewDto.*;
import com.lms.enrollment.service.CourseReviewService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Đánh giá khóa học (UC23) và Admin kiểm duyệt (UC44) — F2.2.
 *
 * <p>{@code GET/POST .../courses/{courseId}/reviews} nằm dưới {@code /api/v1/courses/**} nên tự
 * động public theo {@code SecurityConfig} cho GET; POST vẫn cần role STUDENT qua
 * {@code @PreAuthorize}. {@code /api/v1/reviews/**} (Admin) không nằm trong danh sách public nên
 * mặc định yêu cầu đăng nhập, cộng thêm {@code @PreAuthorize} kiểm role.
 */
@RestController
@RequiredArgsConstructor
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    @GetMapping("/api/v1/courses/{courseId}/reviews")
    public ResponseEntity<Page<Res>> listForCourse(
            @PathVariable Long courseId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(courseReviewService.listForCourse(courseId, pageable));
    }

    @PostMapping("/api/v1/courses/{courseId}/reviews")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Res> create(
            Principal principal, @PathVariable Long courseId, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(courseReviewService.create(principal.getName(), courseId, req));
    }

    @GetMapping("/api/v1/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Res>> listAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(courseReviewService.listAll(pageable));
    }

    @PostMapping("/api/v1/reviews/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> hide(@PathVariable Long id) {
        return ResponseEntity.ok(courseReviewService.hide(id));
    }

    @PostMapping("/api/v1/reviews/{id}/unhide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Res> unhide(@PathVariable Long id) {
        return ResponseEntity.ok(courseReviewService.unhide(id));
    }
}
