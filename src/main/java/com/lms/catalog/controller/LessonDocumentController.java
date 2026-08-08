package com.lms.catalog.controller;

import com.lms.catalog.dto.LessonDocumentDto.Res;
import com.lms.catalog.service.LessonDocumentService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Tài liệu đính kèm bài học (Giai đoạn 4, UC35). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class LessonDocumentController {

    private final LessonDocumentService lessonDocumentService;

    @GetMapping("/api/v1/lessons/{lessonId}/documents")
    public ResponseEntity<List<Res>> list(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonDocumentService.list(principal.getName(), lessonId));
    }

    @PostMapping(value = "/api/v1/lessons/{lessonId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Res> upload(
            Principal principal, @PathVariable Long lessonId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(lessonDocumentService.upload(principal.getName(), lessonId, file));
    }

    @DeleteMapping("/api/v1/lesson-documents/{documentId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long documentId) {
        lessonDocumentService.delete(principal.getName(), documentId);
        return ResponseEntity.noContent().build();
    }

    /** BR-COURSE-06 — Admin xem tài liệu đính kèm để kiểm duyệt (ghi đè @PreAuthorize lớp cha). */
    @GetMapping("/api/v1/courses/moderation/lessons/{lessonId}/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Res>> listForModeration(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonDocumentService.listForModeration(lessonId));
    }
}
