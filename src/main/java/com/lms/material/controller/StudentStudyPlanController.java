package com.lms.material.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.dto.StudyPlanDto;
import com.lms.material.dto.StudyPlanReq;
import com.lms.material.service.StudentStudyPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/student/courses/{courseId}/study-plan")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentStudyPlanController {

    private final StudentStudyPlanService studyPlanService;
    private final UserRepository userRepository;

    private Long getUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getName()))
                .getId();
    }

    @GetMapping
    public ResponseEntity<StudyPlanDto> getPlan(
            @PathVariable Long courseId,
            Principal principal) {
        // BUG THẬT (03/10/2026) — trước đây trả 204 No Content khi chưa có plan, nhưng FE
        // (CourseStudyPlanTab.tsx) lại bắt lỗi theo `err.status === 404` để coi là "chưa có
        // plan" — 2 bên không khớp hợp đồng API (chạy được là NHỜ 204 vẫn resolve thành công
        // với body rỗng, không phải vì FE xử lý đúng case 204). Đổi BE trả đúng 404 để khớp FE.
        StudyPlanDto plan = studyPlanService.getPlan(getUserId(principal), courseId);
        if (plan == null) {
            throw new ResourceNotFoundException("StudyPlan", courseId);
        }
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/generate")
    public ResponseEntity<StudyPlanDto> generatePlan(
            @PathVariable Long courseId,
            @Valid @RequestBody StudyPlanReq req,
            Principal principal) {
        return ResponseEntity.ok(studyPlanService.generatePlan(getUserId(principal), courseId, req));
    }

    @DeleteMapping
    public ResponseEntity<Void> deletePlan(
            @PathVariable Long courseId,
            Principal principal) {
        studyPlanService.deletePlan(getUserId(principal), courseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reschedule")
    public ResponseEntity<StudyPlanDto> reschedulePlan(
            @PathVariable Long courseId,
            @RequestBody(required = false) RescheduleRequest req,
            Principal principal) {
        
        java.time.LocalDate targetDate = (req != null && req.getTargetDate() != null)
                ? java.time.LocalDate.parse(req.getTargetDate())
                : null;
                
        return ResponseEntity.ok(studyPlanService.reschedulePlan(getUserId(principal), courseId, targetDate));
    }

    @lombok.Data
    public static class RescheduleRequest {
        private String targetDate;
    }
}
