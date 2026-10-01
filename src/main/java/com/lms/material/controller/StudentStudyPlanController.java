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
        StudyPlanDto plan = studyPlanService.getPlan(getUserId(principal), courseId);
        if (plan == null) {
            return ResponseEntity.noContent().build();
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
}
