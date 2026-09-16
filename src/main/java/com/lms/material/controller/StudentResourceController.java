package com.lms.material.controller;

import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.entity.CourseResource;
import com.lms.material.repository.CourseResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/resources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentResourceController {

    private final CourseResourceRepository courseResourceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCourseResources(Principal principal, @PathVariable Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        
        // Ensure student is enrolled
        if (!enrollmentRepository.existsByUser_EmailAndCourse_Id(principal.getName(), courseId)) {
            throw new AccessDeniedDomainException("Bạn chưa ghi danh khóa học này");
        }

        List<CourseResource> resources = courseResourceRepository.findByCourse_IdAndIsDeletedFalseOrderByCreatedAtDesc(courseId);
        
        List<Map<String, Object>> result = resources.stream().map(r -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("title", r.getTitle());
            map.put("fileUrl", r.getFileUrl());
            map.put("fileSize", r.getFileSize() != null ? r.getFileSize() : 0);
            map.put("fileType", r.getFileType() != null ? r.getFileType() : "");
            map.put("chapterId", r.getChapter() != null ? r.getChapter().getId() : null);
            map.put("lessonId", r.getLesson() != null ? r.getLesson().getId() : null);
            map.put("createdAt", r.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
