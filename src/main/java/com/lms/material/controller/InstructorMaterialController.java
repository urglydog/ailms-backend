package com.lms.material.controller;

import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.FlashcardDeck;
import com.lms.material.entity.Mindmap;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.MindmapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/instructor/materials")
@RequiredArgsConstructor
public class InstructorMaterialController {

    private final MindmapRepository mindmapRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final com.lms.material.repository.MaterialGenerationRepository materialGenerationRepository;
    private final com.lms.material.repository.QuizRepository quizRepository;
    private final com.lms.material.repository.QuizAttemptRepository quizAttemptRepository;

    @PutMapping("/mindmaps/{id}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> setMindmapOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        Mindmap mindmap = mindmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mindmap", id));
        if (!mindmap.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        mindmap.setIsOfficial(isOfficial);
        mindmapRepository.save(mindmap);
        return ResponseEntity.ok(java.util.Map.of("message", isOfficial ? "Đã đặt làm học liệu chính thức" : "Đã hủy học liệu chính thức"));
    }

    @PutMapping("/flashcards/{id}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> setFlashcardOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        FlashcardDeck deck = flashcardDeckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlashcardDeck", id));
        if (!deck.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        deck.setIsOfficial(isOfficial);
        flashcardDeckRepository.save(deck);
        return ResponseEntity.ok(java.util.Map.of("message", isOfficial ? "Đã đặt làm học liệu chính thức" : "Đã hủy học liệu chính thức"));
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMaterialsForCourse(Principal principal, @PathVariable Long courseId) {
        // Fetch all generated materials for the course
        java.util.List<com.lms.material.entity.MaterialGeneration> generations = 
            materialGenerationRepository.findByCourse_IdOrderByCreatedAtDesc(courseId);
                
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.lms.material.entity.MaterialGeneration gen : generations) {
            boolean createdByInstructor = gen.getUser() != null && gen.getUser().getEmail().equals(principal.getName());
            
            boolean isOfficial = false;
            Long materialId = null;
            Integer questionCount = 0;
            Integer randomPickCount = null;
            Boolean allowReview = true;
            java.time.LocalDateTime startTime = null;
            java.time.LocalDateTime endTime = null;
            Integer durationMinutes = null;
            Integer maxAttempts = null;
            Boolean isProctored = false;
            Integer maxViolations = 3;

            if (gen.getStatus() == com.lms.common.enums.GenStatus.COMPLETED) {
                if (gen.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
                    var m = mindmapRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
                    if (m != null) {
                        materialId = m.getId();
                        isOfficial = m.getIsOfficial();
                    }
                } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
                    var f = flashcardDeckRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
                    if (f != null) {
                        materialId = f.getId();
                        isOfficial = f.getIsOfficial();
                    }
                } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
                    var q = quizRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
                    if (q != null) {
                        materialId = q.getId();
                        isOfficial = q.getIsOfficial();
                        questionCount = q.getQuestionCount();
                        randomPickCount = q.getRandomPickCount();
                        allowReview = q.getAllowReview();
                        startTime = q.getStartTime();
                        endTime = q.getEndTime();
                        durationMinutes = q.getDurationMinutes();
                        maxAttempts = q.getMaxAttempts();
                        isProctored = q.getIsProctored();
                        maxViolations = q.getMaxViolations();
                    }
                }
            }

            Integer attemptCount = 0;
            if (materialId != null && gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
                attemptCount = quizAttemptRepository.findByUser_EmailAndQuiz_IdOrderByScoreDesc(principal.getName(), materialId).size();
            }

            // CHỈ GIỮ LẠI: Học liệu do Giảng viên tự sinh HOẶC học liệu đang là Official.
            // Bỏ qua các học liệu tự luyện cá nhân của Học viên.
            if (createdByInstructor || isOfficial) {
                result.add(java.util.Map.ofEntries(
                        java.util.Map.entry("id", gen.getId()),
                        java.util.Map.entry("materialType", gen.getMaterialType().name()),
                        java.util.Map.entry("title", gen.getTitle() != null ? gen.getTitle() : ""),
                        java.util.Map.entry("language", gen.getLanguage()),
                        java.util.Map.entry("createdAt", gen.getCreatedAt()),
                        java.util.Map.entry("status", gen.getStatus().name()),
                        java.util.Map.entry("isOfficial", isOfficial),
                        java.util.Map.entry("versionNo", gen.getVersionNo()),
                        java.util.Map.entry("materialId", materialId != null ? materialId : ""),
                        java.util.Map.entry("questionCount", questionCount != null ? questionCount : 0),
                        java.util.Map.entry("randomPickCount", randomPickCount != null ? randomPickCount : ""),
                        java.util.Map.entry("allowReview", allowReview),
                        java.util.Map.entry("startTime", startTime != null ? startTime.toString() : ""),
                        java.util.Map.entry("endTime", endTime != null ? endTime.toString() : ""),
                        java.util.Map.entry("durationMinutes", durationMinutes != null ? durationMinutes : ""),
                        java.util.Map.entry("maxAttempts", maxAttempts != null ? maxAttempts : ""),
                        java.util.Map.entry("attemptCount", attemptCount),
                        java.util.Map.entry("isProctored", isProctored),
                        java.util.Map.entry("maxViolations", maxViolations != null ? maxViolations : 3)
                ));
            }
        }
        return ResponseEntity.ok(result);
    }
}
