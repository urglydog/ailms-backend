package com.lms.bundle.controller;

import com.lms.bundle.dto.CourseBundleDto;
import com.lms.bundle.service.CourseBundleService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseBundleController {

    private final CourseBundleService bundleService;

    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @PostMapping("/instructor/bundles")
    public ResponseEntity<CourseBundleDto.Res> createBundle(
            Principal principal,
            @RequestBody CourseBundleDto.CreateReq req) {
        return ResponseEntity.ok(bundleService.createBundle(principal.getName(), req));
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @PutMapping("/instructor/bundles/{id}")
    public ResponseEntity<CourseBundleDto.Res> updateBundle(
            Principal principal,
            @PathVariable Long id,
            @RequestBody CourseBundleDto.UpdateReq req) {
        return ResponseEntity.ok(bundleService.updateBundle(id, principal.getName(), req));
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @GetMapping("/instructor/bundles")
    public ResponseEntity<Page<CourseBundleDto.Res>> getBundles(
            Principal principal,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CourseBundleDto.Res> page = bundleService.getBundlesByInstructor(principal.getName(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/courses/{courseId}/bundles")
    public ResponseEntity<List<CourseBundleDto.Res>> getBundlesForCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(bundleService.getActiveBundlesForCourse(courseId));
    }

    /** Gộp tra bundle cho nhiều courseId (giỏ hàng tự phát hiện combo) — tránh N+1 request khi
     * giỏ có nhiều khóa. Public, giống {@link #getBundlesForCourse}. */
    @GetMapping("/courses/bundles/active")
    public ResponseEntity<List<CourseBundleDto.Res>> getBundlesForCourses(@RequestParam List<Long> courseIds) {
        return ResponseEntity.ok(bundleService.getActiveBundlesForCourses(courseIds));
    }
}
