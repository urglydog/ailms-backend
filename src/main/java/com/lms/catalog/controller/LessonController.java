package com.lms.catalog.controller;

import com.lms.catalog.dto.LessonDto.*;
import com.lms.catalog.service.LessonService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
