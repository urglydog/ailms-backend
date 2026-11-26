package com.lms.note.controller;

import com.lms.note.dto.LessonNoteDto.CreateReq;
import com.lms.note.dto.LessonNoteDto.NoteRes;
import com.lms.note.dto.LessonNoteDto.UpdateReq;
import com.lms.note.service.LessonNoteService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Ghi chú gắn mốc thời gian video (26/11/2026). Mọi thao tác chỉ trên ghi chú của chính mình. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class LessonNoteController {

    private final LessonNoteService noteService;

    @GetMapping("/api/v1/lessons/{lessonId}/notes")
    public ResponseEntity<List<NoteRes>> list(
            Principal principal, @PathVariable Long lessonId,
            @RequestParam(defaultValue = "lesson") String scope) {
        return ResponseEntity.ok(noteService.list(principal.getName(), lessonId, scope));
    }

    @PostMapping("/api/v1/lessons/{lessonId}/notes")
    public ResponseEntity<NoteRes> create(
            Principal principal, @PathVariable Long lessonId, @Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(noteService.create(principal.getName(), lessonId, req));
    }

    @PutMapping("/api/v1/notes/{noteId}")
    public ResponseEntity<NoteRes> update(
            Principal principal, @PathVariable Long noteId, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(noteService.update(principal.getName(), noteId, req));
    }

    @DeleteMapping("/api/v1/notes/{noteId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long noteId) {
        noteService.delete(principal.getName(), noteId);
        return ResponseEntity.noContent().build();
    }
}
