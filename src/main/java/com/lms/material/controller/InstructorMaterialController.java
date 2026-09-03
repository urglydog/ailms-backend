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

    @PutMapping("/mindmaps/{id}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Void> setMindmapOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        Mindmap mindmap = mindmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mindmap", id));
        if (!mindmap.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        mindmap.setIsOfficial(isOfficial);
        mindmapRepository.save(mindmap);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/flashcards/{id}/set-official")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<Void> setFlashcardOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        FlashcardDeck deck = flashcardDeckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlashcardDeck", id));
        if (!deck.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        deck.setIsOfficial(isOfficial);
        flashcardDeckRepository.save(deck);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional(readOnly = true)
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMaterialsForCourse(Principal principal, @PathVariable Long courseId) {
        // Fetch all generated materials for the course
        java.util.List<com.lms.material.entity.MaterialGeneration> generations = 
            com.lms.context.ApplicationContextProvider.getApplicationContext()
                .getBean(com.lms.material.repository.MaterialGenerationRepository.class)
                .findByCourse_IdOrderByCreatedAtDesc(courseId);
                
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.lms.material.entity.MaterialGeneration gen : generations) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", gen.getId());
            map.put("materialType", gen.getMaterialType());
            map.put("title", gen.getTitle());
            map.put("createdAt", gen.getCreatedAt());
            map.put("status", gen.getStatus());
            map.put("isOfficial", false); // default
            
            if (gen.getStatus() == com.lms.common.enums.GenStatus.COMPLETED) {
                if (gen.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
                    mindmapRepository.findByMaterialGeneration_Id(gen.getId()).ifPresent(m -> {
                        map.put("materialId", m.getId());
                        map.put("isOfficial", m.getIsOfficial());
                    });
                } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
                    flashcardDeckRepository.findByMaterialGeneration_Id(gen.getId()).ifPresent(f -> {
                        map.put("materialId", f.getId());
                        map.put("isOfficial", f.getIsOfficial());
                    });
                } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
                    com.lms.context.ApplicationContextProvider.getApplicationContext()
                        .getBean(com.lms.material.repository.QuizRepository.class)
                        .findByMaterialGeneration_Id(gen.getId()).ifPresent(q -> {
                            map.put("materialId", q.getId());
                            map.put("isOfficial", q.getIsOfficial());
                            map.put("randomPickCount", q.getRandomPickCount());
                            map.put("allowReview", q.getAllowReview());
                            map.put("startTime", q.getStartTime());
                            map.put("endTime", q.getEndTime());
                            map.put("durationMinutes", q.getDurationMinutes());
                            map.put("maxAttempts", q.getMaxAttempts());
                        });
                }
            }
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }
}
