package com.lms.material.controller;

import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.FlashcardDeck;
import com.lms.material.entity.Mindmap;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.MindmapRepository;
import com.lms.common.service.NotificationService;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.entity.Enrollment;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
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
    private final com.lms.material.repository.FlashcardReviewRepository flashcardReviewRepository;
    private final NotificationService notificationService;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    @PutMapping("/{id}/attach-lesson")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> attachToLesson(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Long> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Long lessonId = payload.get("lessonId");
        Long chapterId = payload.get("chapterId");
        
        if (lessonId != null) {
            com.lms.catalog.entity.Lesson lesson = new com.lms.catalog.entity.Lesson();
            lesson.setId(lessonId);
            gen.setLesson(lesson);
            gen.setChapter(null);
        } else if (chapterId != null) {
            com.lms.catalog.entity.Chapter chapter = new com.lms.catalog.entity.Chapter();
            chapter.setId(chapterId);
            gen.setChapter(chapter);
            gen.setLesson(null);
        } else {
            gen.setLesson(null);
            gen.setChapter(null);
        }
        
        materialGenerationRepository.save(gen);
        return ResponseEntity.ok(java.util.Map.of("message", "Đã cập nhật đính kèm học liệu"));
    }

    @PutMapping("/mindmaps/{id}/set-official")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> setMindmapOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        Mindmap mindmap = mindmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mindmap", id));
        if (!mindmap.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        mindmap.setIsOfficial(isOfficial);
        mindmapRepository.save(mindmap);

        if (isOfficial) {
            String title = "Học liệu mới: " + (mindmap.getMaterialGeneration().getTitle() != null ? mindmap.getMaterialGeneration().getTitle() : "Sơ đồ tư duy");
            String content = "Giảng viên vừa công bố một Sơ đồ tư duy mới cho khóa học của bạn.";
            String linkUrl = "/materials/" + mindmap.getMaterialGeneration().getId();
            java.util.List<Enrollment> enrollments = enrollmentRepository.findByCourseId(mindmap.getMaterialGeneration().getCourse().getId());
            for (Enrollment e : enrollments) {
                notificationService.notify(e.getUser().getId(), "NEW_OFFICIAL_MATERIAL", title, content, linkUrl);
            }
        }

        return ResponseEntity.ok(java.util.Map.of("message", isOfficial ? "Đã đặt làm học liệu chính thức" : "Đã hủy học liệu chính thức"));
    }

    @PutMapping("/flashcards/{id}/set-official")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> setFlashcardOfficial(Principal principal, @PathVariable Long id, @RequestParam(defaultValue = "true") boolean isOfficial) {
        FlashcardDeck deck = flashcardDeckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FlashcardDeck", id));
        if (!deck.getMaterialGeneration().getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        deck.setIsOfficial(isOfficial);
        flashcardDeckRepository.save(deck);

        if (isOfficial) {
            String title = "Học liệu mới: " + (deck.getMaterialGeneration().getTitle() != null ? deck.getMaterialGeneration().getTitle() : "Bộ Flashcard");
            String content = "Giảng viên vừa công bố một Bộ thẻ Flashcard mới cho khóa học của bạn.";
            String linkUrl = "/materials/" + deck.getMaterialGeneration().getId();
            java.util.List<Enrollment> enrollments = enrollmentRepository.findByCourseId(deck.getMaterialGeneration().getCourse().getId());
            for (Enrollment e : enrollments) {
                notificationService.notify(e.getUser().getId(), "NEW_OFFICIAL_MATERIAL", title, content, linkUrl);
            }
        }

        return ResponseEntity.ok(java.util.Map.of("message", isOfficial ? "Đã đặt làm học liệu chính thức" : "Đã hủy học liệu chính thức"));
    }

    @PostMapping("/courses/{courseId}/manual")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, Object>> createManualMaterial(Principal principal, @PathVariable Long courseId, @RequestBody java.util.Map<String, String> payload) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (!course.getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }

        String materialTypeStr = payload.get("materialType");
        String language = payload.get("language");
        String title = payload.get("title");

        com.lms.common.enums.MaterialType materialType = com.lms.common.enums.MaterialType.valueOf(materialTypeStr);

        int nextVersion = materialGenerationRepository.findTopByUser_IdAndCourse_IdAndIsDeletedFalseOrderByVersionNoDesc(course.getInstructor().getId(), course.getId())
                .map(mg -> mg.getVersionNo() + 1)
                .orElse(1);

        com.lms.material.entity.MaterialGeneration generation = new com.lms.material.entity.MaterialGeneration();
        generation.setUser(course.getInstructor());
        generation.setCourse(course);
        generation.setMaterialType(materialType);
        generation.setLanguage(language);
        generation.setTitle(title);
        
        // Auto-binding cho Manual
        String scopeStr = payload.get("scope");
        String scopeRefIdStr = payload.get("scopeRefId");
        String customLessonIdsStr = payload.get("customLessonIds");
        
        if ("LESSON".equals(scopeStr) && scopeRefIdStr != null) {
            com.lms.catalog.entity.Lesson lesson = new com.lms.catalog.entity.Lesson();
            lesson.setId(Long.parseLong(scopeRefIdStr));
            generation.setLesson(lesson);
        } else if ("CHAPTER".equals(scopeStr) && scopeRefIdStr != null) {
            com.lms.catalog.entity.Chapter chapter = new com.lms.catalog.entity.Chapter();
            chapter.setId(Long.parseLong(scopeRefIdStr));
            generation.setChapter(chapter);
        } else if ("CUSTOM".equals(scopeStr) && customLessonIdsStr != null) {
            generation.setCustomLessonIds(customLessonIdsStr); // JSON string
        }
        
        generation.setScopeType(com.lms.common.enums.ScopeType.WHOLE_COURSE);
        if ("LESSON".equals(scopeStr) || "CUSTOM".equals(scopeStr)) {
            generation.setScopeType(com.lms.common.enums.ScopeType.CUSTOM_LESSONS);
        } else if ("CHAPTER".equals(scopeStr)) {
            generation.setScopeType(com.lms.common.enums.ScopeType.CHAPTER);
        }
        
        generation.setVersionNo(nextVersion);
        generation.setStatus(com.lms.common.enums.GenStatus.COMPLETED); // Completed immediately since manual

        String quizTypeStr = payload.get("quizType");
        
        materialGenerationRepository.save(generation);

        Long materialId = null;

        if (materialType == com.lms.common.enums.MaterialType.QUIZ) {
            com.lms.material.entity.Quiz quiz = new com.lms.material.entity.Quiz();
            quiz.setMaterialGeneration(generation);
            quiz.setQuestionCount(0);
            quiz.setIsOfficial(false);
            if (quizTypeStr != null && quizTypeStr.equals("LECTURE_QUIZ")) {
                quiz.setQuizType(com.lms.common.enums.QuizType.LECTURE_QUIZ);
            } else {
                quiz.setQuizType(com.lms.common.enums.QuizType.OFFICIAL_EXAM);
            }
            
            if (payload.containsKey("allowReview") && payload.get("allowReview") != null) {
                quiz.setAllowReview(Boolean.parseBoolean(payload.get("allowReview")));
            }
            if (payload.containsKey("maxAttempts") && payload.get("maxAttempts") != null && !payload.get("maxAttempts").trim().isEmpty()) {
                quiz.setMaxAttempts(Integer.parseInt(payload.get("maxAttempts")));
            }
            if (payload.containsKey("durationMinutes") && payload.get("durationMinutes") != null && !payload.get("durationMinutes").trim().isEmpty()) {
                quiz.setDurationMinutes(Integer.parseInt(payload.get("durationMinutes")));
            }
            
            quiz = quizRepository.save(quiz);
            materialId = quiz.getId();
        } else if (materialType == com.lms.common.enums.MaterialType.FLASHCARD) {
            FlashcardDeck deck = new FlashcardDeck();
            deck.setMaterialGeneration(generation);
            deck.setCardCount(0);
            deck.setIsOfficial(false);
            deck = flashcardDeckRepository.save(deck);
            materialId = deck.getId();
        } else if (materialType == com.lms.common.enums.MaterialType.MINDMAP) {
            Mindmap mindmap = new Mindmap();
            mindmap.setMaterialGeneration(generation);
            mindmap.setNodeCount(0);
            mindmap.setMermaidCode("mindmap\n  root((\"Tâm điểm\"))\n    Nhánh 1\n    Nhánh 2");
            mindmap.setIsOfficial(false);
            mindmap = mindmapRepository.save(mindmap);
            materialId = mindmap.getId();
        }

        return ResponseEntity.ok(java.util.Map.of("id", generation.getId(), "materialId", materialId));
    }

    @GetMapping("/courses/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMaterialsForCourse(Principal principal, @PathVariable Long courseId) {
        // Fetch all generated materials for the course
        java.util.List<com.lms.material.entity.MaterialGeneration> generations = 
            materialGenerationRepository.findByCourse_IdAndIsDeletedFalseOrderByCreatedAtDesc(courseId);
                
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.lms.material.entity.MaterialGeneration gen : generations) {
            if (gen.getStatus() == com.lms.common.enums.GenStatus.ARCHIVED) {
                continue;
            }
            
            boolean createdByInstructor = gen.getUser() != null && gen.getUser().getEmail().equals(principal.getName());
            
            boolean isOfficial = false;
            Long materialId = null;
            Long chapterId = gen.getChapter() != null ? gen.getChapter().getId() : null;
            Long lessonId = gen.getLesson() != null ? gen.getLesson().getId() : null;
            String quizType = "OFFICIAL_EXAM";
            Integer questionCount = 0;
            Integer randomPickCount = null;
            Boolean allowReview = true;
            java.time.LocalDateTime startTime = null;
            java.time.LocalDateTime endTime = null;
            Integer durationMinutes = null;
            Integer maxAttempts = null;
            Boolean isProctored = false;
            Integer maxViolations = 3;
            Integer usageCount = 0;

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
                        usageCount = flashcardReviewRepository.countByFlashcard_FlashcardDeck_Id(f.getId());
                    }
                } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
                    var q = quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(gen.getId()).orElse(null);
                    if (q != null) {
                        materialId = q.getId();
                        isOfficial = q.getIsOfficial();
                        usageCount = quizAttemptRepository.countByQuiz_Id(q.getId());
                        quizType = q.getQuizType() != null ? q.getQuizType().name() : "OFFICIAL_EXAM";
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
                        java.util.Map.entry("chapterId", chapterId != null ? chapterId : ""),
                        java.util.Map.entry("lessonId", lessonId != null ? lessonId : ""),
                        java.util.Map.entry("quizType", quizType),
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
