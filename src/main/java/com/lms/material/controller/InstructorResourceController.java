package com.lms.material.controller;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.ChapterRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.repository.LessonRepository;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.storage.StorageService;
import com.lms.material.entity.CourseResource;
import com.lms.material.repository.CourseResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/instructor/resources")
@RequiredArgsConstructor
public class InstructorResourceController {

    private final CourseResourceRepository courseResourceRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final StorageService storageService;

    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of(
        "application/pdf", 
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.rar"
    );

    @PostMapping("/courses/{courseId}/upload")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadResource(
            Principal principal,
            @PathVariable Long courseId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "lessonId", required = false) Long lessonId) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        if (files == null || files.length == 0) {
            throw new com.lms.common.exception.InvalidRequestException("Danh sách file trống");
        }

        List<Map<String, Object>> successes = new java.util.ArrayList<>();
        List<Map<String, String>> failures = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "unknown";
            
            if (file.isEmpty()) {
                failures.add(Map.of("file", originalName, "reason", "File trống"));
                continue;
            }
            
            if (file.getContentType() != null && !ALLOWED_MIME_TYPES.contains(file.getContentType())) {
                failures.add(Map.of("file", originalName, "reason", "Định dạng không hợp lệ"));
                continue;
            }

            String key = "resources/" + UUID.randomUUID() + "-" + originalName;
            String url;
            try {
                url = storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
            } catch (java.io.IOException e) {
                failures.add(Map.of("file", originalName, "reason", "Lỗi upload: " + e.getMessage()));
                continue;
            }

            CourseResource resource = new CourseResource();
            resource.setCourse(course);
            resource.setTitle(originalName);
            resource.setFileUrl(url);
            resource.setFileSize(file.getSize());
            resource.setFileType(file.getContentType());

            if (lessonId != null) {
                Lesson lesson = lessonRepository.findById(lessonId)
                        .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
                resource.setLesson(lesson);
            } else if (chapterId != null) {
                Chapter chapter = chapterRepository.findById(chapterId)
                        .orElseThrow(() -> new ResourceNotFoundException("Chapter", chapterId));
                resource.setChapter(chapter);
            }

            courseResourceRepository.save(resource);
            
            successes.add(Map.of(
                    "id", resource.getId(),
                    "title", resource.getTitle(),
                    "fileUrl", resource.getFileUrl()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "successes", successes,
                "failures", failures
        ));
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCourseResources(@PathVariable Long courseId) {
        List<CourseResource> resources = courseResourceRepository.findByCourse_IdAndIsDeletedFalseOrderByCreatedAtDesc(courseId);
        
        List<Map<String, Object>> result = resources.stream().map(r -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("title", r.getTitle());
            map.put("fileUrl", r.getFileUrl());
            map.put("fileSize", r.getFileSize() != null ? r.getFileSize() : 0);
            map.put("fileType", r.getFileType() != null ? r.getFileType() : "");
            map.put("chapterId", r.getChapter() != null ? r.getChapter().getId() : "");
            map.put("lessonId", r.getLesson() != null ? r.getLesson().getId() : "");
            map.put("createdAt", r.getCreatedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteResource(Principal principal, @PathVariable Long id) {
        CourseResource resource = courseResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseResource", id));
        if (!resource.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        resource.setIsDeleted(true);
        resource.setDeletedAt(java.time.LocalDateTime.now());
        courseResourceRepository.save(resource);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tài nguyên tĩnh"));
    }
}
