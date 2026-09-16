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

    @PostMapping("/courses/{courseId}/upload")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadResource(
            Principal principal,
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "lessonId", required = false) Long lessonId) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }

        String key = "resources/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        String url;
        try {
            url = storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Upload failed", e);
        }

        CourseResource resource = new CourseResource();
        resource.setCourse(course);
        resource.setTitle(title);
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

        return ResponseEntity.ok(Map.of(
                "id", resource.getId(),
                "title", resource.getTitle(),
                "fileUrl", resource.getFileUrl()
        ));
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getCourseResources(@PathVariable Long courseId) {
        List<CourseResource> resources = courseResourceRepository.findByCourse_IdOrderByCreatedAtDesc(courseId);
        
        List<Map<String, Object>> result = resources.stream().map(r -> Map.of(
                "id", r.getId(),
                "title", r.getTitle(),
                "fileUrl", r.getFileUrl(),
                "fileSize", r.getFileSize() != null ? r.getFileSize() : 0,
                "fileType", r.getFileType() != null ? r.getFileType() : "",
                "chapterId", r.getChapter() != null ? r.getChapter().getId() : "",
                "lessonId", r.getLesson() != null ? r.getLesson().getId() : "",
                "createdAt", r.getCreatedAt().toString()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteResource(Principal principal, @PathVariable Long id) {
        CourseResource resource = courseResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseResource", id));
        if (!resource.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        courseResourceRepository.delete(resource);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tài nguyên tĩnh"));
    }
}
