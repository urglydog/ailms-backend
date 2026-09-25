package com.lms.catalog.controller;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.service.CourseEmbeddingService;
import com.lms.common.enums.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UC49 nâng cấp (25/09/2026) — backfill embedding cho các khóa học đã tồn tại TRƯỚC khi tính
 * năng này ra đời (hook ở {@code CourseService}/{@code ChapterService}/{@code LessonService} chỉ
 * bắt được thay đổi TỪ NAY về sau). Chạy TAY 1 lần qua curl với
 * {@code X-Internal-Token}, xác thực bằng {@link com.lms.common.security.InternalApiTokenFilter}
 * giống toàn bộ {@code /api/internal/**} khác. Idempotent — gọi lại nhiều lần vô hại (embedding
 * bên Supabase upsert theo {@code course_id}), an toàn để re-seed khi cần.
 *
 * <p>Bao gồm cả khóa DRAFT/PENDING/REJECTED (không chỉ PUBLISHED) — để khi khóa được duyệt và lên
 * public, embedding đã có sẵn thay vì phải chờ instructor sửa lại tiêu đề/mô tả mới có.
 */
@RestController
@RequestMapping("/api/internal/courses/embeddings")
@RequiredArgsConstructor
public class InternalCourseEmbeddingController {

    private final CourseRepository courseRepository;
    private final CourseEmbeddingService courseEmbeddingService;

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill() {
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> c.getStatus() != CourseStatus.ARCHIVED)
                .toList();
        courses.forEach(courseEmbeddingService::requestEmbedding);
        return ResponseEntity.ok(Map.of("enqueued", courses.size()));
    }
}
