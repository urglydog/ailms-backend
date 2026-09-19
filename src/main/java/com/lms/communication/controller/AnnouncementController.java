package com.lms.communication.controller;

import com.lms.communication.dto.AnnouncementDto.CreateReq;
import com.lms.communication.dto.AnnouncementDto.Res;
import com.lms.communication.service.AnnouncementService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping("/api/v1/instructor/announcements")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Res> create(Principal principal, @RequestBody CreateReq req) {
        return ResponseEntity.ok(announcementService.create(principal.getName(), req));
    }

    @GetMapping("/api/v1/instructor/announcements")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Res>> listForInstructor(
            Principal principal, @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(announcementService.listForInstructor(principal.getName(), courseId));
    }

    @DeleteMapping("/api/v1/instructor/announcements/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        announcementService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/courses/{courseId}/announcements")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<Res>> listForStudent(Principal principal, @PathVariable Long courseId) {
        return ResponseEntity.ok(announcementService.listForStudent(principal.getName(), courseId));
    }
}
