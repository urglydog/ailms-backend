package com.lms.catalog.controller;

import com.lms.catalog.dto.ChapterDto.*;
import com.lms.catalog.service.ChapterService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class ChapterController {

    private final ChapterService chapterService;

    @PostMapping("/api/v1/courses/mine/{courseId}/chapters")
    public ResponseEntity<Res> create(Principal principal, @PathVariable Long courseId, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(chapterService.create(principal.getName(), courseId, req));
    }

    @PutMapping("/api/v1/chapters/{id}")
    public ResponseEntity<Res> update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(chapterService.update(principal.getName(), id, req));
    }

    @DeleteMapping("/api/v1/chapters/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        chapterService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/courses/mine/{courseId}/chapters/reorder")
    public ResponseEntity<Void> reorder(Principal principal, @PathVariable Long courseId, @Valid @RequestBody ReorderReq req) {
        chapterService.reorder(principal.getName(), courseId, req);
        return ResponseEntity.noContent().build();
    }
}
