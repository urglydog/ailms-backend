package com.lms.catalog.controller;

import com.lms.catalog.dto.CourseDto.*;
import com.lms.catalog.service.CourseService;
import com.lms.common.enums.CourseStatus;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Vòng đời khóa học phía Giảng viên (UC31, UC36) và Admin (UC42) — F2.1.
 *
 * <p><b>Ranh giới:</b> endpoint duyệt/tìm kiếm công khai ({@code GET /courses},
 * {@code GET /courses/{slug}}) KHÔNG nằm trong controller này — thuộc F2.2, nên tạo ở
 * một class riêng (vd. {@code CoursePublicController}) để tránh xung đột merge.
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DetailRes> create(Principal principal, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(courseService.create(principal.getName(), req));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Page<SummaryRes>> getMine(
            Principal principal,
            @RequestParam(required = false) CourseStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(courseService.getMine(principal.getName(), status, pageable));
    }

    @GetMapping("/mine/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DetailRes> getMineDetail(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(courseService.getMineDetail(principal.getName(), id));
    }

    @PutMapping("/mine/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DetailRes> update(
            Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(courseService.update(principal.getName(), id, req));
    }

    /** Giai đoạn 4 — upload ảnh bìa thật lên B2, thay ô nhập URL text tạm thời của F2.1. */
    @PostMapping(value = "/mine/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DetailRes> uploadThumbnail(
            Principal principal, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(courseService.uploadThumbnail(principal.getName(), id, file));
    }

    @PostMapping("/mine/{id}/submit")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DetailRes> submit(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(courseService.submitForReview(principal.getName(), id));
    }

    @DeleteMapping("/mine/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        courseService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<SummaryRes>> getModerationList(
            @RequestParam(required = false) CourseStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(courseService.getModerationList(status, pageable));
    }

    @GetMapping("/moderation/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DetailRes> getModerationDetail(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getModerationDetail(id));
    }

    @PostMapping("/moderation/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DetailRes> approve(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.approve(id));
    }

    @PostMapping("/moderation/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DetailRes> reject(@PathVariable Long id, @Valid @RequestBody RejectReq req) {
        return ResponseEntity.ok(courseService.reject(id, req));
    }
}
