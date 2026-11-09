package com.lms.communication.controller;

import com.lms.communication.dto.AssignmentDto.CreateReq;
import com.lms.communication.dto.AssignmentDto.GradeReq;
import com.lms.communication.dto.AssignmentDto.Res;
import com.lms.communication.dto.AssignmentDto.StudentAssignmentRes;
import com.lms.communication.dto.AssignmentDto.SubmissionRes;
import com.lms.communication.service.AssignmentService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    // ==================== Giảng viên ====================

    @PostMapping("/api/v1/instructor/lessons/{lessonId}/assignments")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Res> create(Principal principal, @PathVariable Long lessonId, @RequestBody CreateReq req) {
        return ResponseEntity.ok(assignmentService.createAssignment(principal.getName(), lessonId, req));
    }

    @GetMapping("/api/v1/instructor/lessons/{lessonId}/assignments")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Res>> listForLesson(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(assignmentService.listForLesson(principal.getName(), lessonId));
    }

    @GetMapping("/api/v1/instructor/assignments")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Res>> listForInstructor(
            Principal principal, @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(assignmentService.listForInstructor(principal.getName(), courseId));
    }

    @DeleteMapping("/api/v1/instructor/assignments/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        assignmentService.deleteAssignment(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/instructor/assignments/{id}/submissions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<SubmissionRes>> listSubmissions(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.listSubmissions(principal.getName(), id));
    }

    @PatchMapping("/api/v1/instructor/assignments/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<SubmissionRes> grade(
            Principal principal, @PathVariable Long submissionId, @RequestBody GradeReq req) {
        return ResponseEntity.ok(
                assignmentService.gradeSubmission(principal.getName(), submissionId, req.score(), req.feedback()));
    }

    // ==================== Học viên ====================

    @GetMapping("/api/v1/lessons/{lessonId}/assignments")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentAssignmentRes>> listForStudent(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(assignmentService.listForStudent(principal.getName(), lessonId));
    }

    @PostMapping(value = "/api/v1/assignments/{assignmentId}/submissions", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<SubmissionRes> submit(
            Principal principal,
            @PathVariable Long assignmentId,
            @RequestParam(required = false) String textContent,
            @RequestParam(required = false) MultipartFile file) {
        return ResponseEntity.ok(assignmentService.submit(principal.getName(), assignmentId, textContent, file));
    }
}
