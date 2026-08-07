package com.lms.catalog.controller;

import com.lms.catalog.dto.CoursePublicDto.*;
import com.lms.catalog.service.CoursePublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Duyệt khóa học công khai (UC09, UC10) — F2.2. Guest/Student không cần đăng nhập.
 *
 * <p><b>Ranh giới với F2.1</b> ({@code CourseController}): controller đó chỉ có các route con
 * literal ({@code /mine/**}, {@code /moderation/**}) cho Giảng viên/Admin — không route nào ở đó
 * là {@code GET /courses} trần hay {@code GET /courses/{slug}}, nên không va chạm dù cùng
 * {@code @RequestMapping("/api/v1/courses")}. Route ở đây tự động public nhờ
 * {@code SecurityConfig.PUBLIC_GET_ENDPOINTS} đã có sẵn {@code "/api/v1/courses/**"}.
 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CoursePublicController {

    private final CoursePublicService coursePublicService;

    @GetMapping
    public ResponseEntity<Page<SummaryRes>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String priceType,
            @RequestParam(required = false) String sortBy,
            @PageableDefault(size = 24, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(coursePublicService.search(keyword, categorySlug, level, priceType, sortBy, pageable));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<DetailRes> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(coursePublicService.getBySlug(slug));
    }
}
