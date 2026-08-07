package com.lms.catalog.controller;

import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.service.LessonService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class LessonController {

    private final LessonService lessonService;

    @PostMapping("/api/v1/chapters/{chapterId}/lessons")
    public ResponseEntity<Res> create(Principal principal, @PathVariable Long chapterId, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(lessonService.create(principal.getName(), chapterId, req));
    }

    @PutMapping("/api/v1/lessons/{id}")
    public ResponseEntity<Res> update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(lessonService.update(principal.getName(), id, req));
    }

    @DeleteMapping("/api/v1/lessons/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        lessonService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/chapters/{chapterId}/lessons/reorder")
    public ResponseEntity<Void> reorder(Principal principal, @PathVariable Long chapterId, @Valid @RequestBody ReorderReq req) {
        lessonService.reorder(principal.getName(), chapterId, req);
        return ResponseEntity.noContent().build();
    }

    /** F4.1 (UC34) — nạp MP4 trực tiếp. Giới hạn kích thước tổng thể do {@code spring.servlet.multipart} (2GB). */
    @PostMapping(value = "/api/v1/lessons/{id}/video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Res> uploadVideo(
            Principal principal, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(lessonService.uploadVideo(principal.getName(), id, file));
    }

    /** F4.1 (UC34) — dán link YouTube công khai thay cho upload MP4. */
    @PostMapping("/api/v1/lessons/{id}/video/youtube")
    public ResponseEntity<Res> setYoutubeVideo(
            Principal principal, @PathVariable Long id, @Valid @RequestBody SetYoutubeReq req) {
        return ResponseEntity.ok(lessonService.setYoutubeVideo(principal.getName(), id, req));
    }
}
