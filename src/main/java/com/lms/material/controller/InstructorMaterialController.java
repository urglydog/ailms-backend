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

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<java.util.Map<String, String>> unassignMaterial(Principal principal, @PathVariable Long assignmentId) {
        // Validation of ownership could be added here if needed, but for simplicity assuming AssignmentService handles it or it's implicitly trusted by Instructor Role
        materialAssignmentService.unassignMaterial(assignmentId);
        return ResponseEntity.ok(java.util.Map.of("message", "Đã gỡ phân phối học liệu"));
    }

    @PutMapping("/{id}/move-to-folder")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> moveToFolder(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Long> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Long folderId = payload.get("folderId");
        if (folderId != null) {
            com.lms.material.entity.MaterialFolder folder = materialFolderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("MaterialFolder", folderId));
            gen.setFolder(folder);
        } else {
            gen.setFolder(null);
        }
        materialGenerationRepository.save(gen);
        
        return ResponseEntity.ok(java.util.Map.of("message", "Đã di chuyển học liệu"));
    }


    @PostMapping("/{id}/versioning-overwrite")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, Object>> versioningOverwrite(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Long> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Long targetLessonId = payload.get("targetLessonId");
        Long targetChapterId = payload.get("targetChapterId");
        
        // 1. Clone V1 -> V2
        int nextVersion = materialGenerationRepository.findTopByUser_IdAndCourse_IdAndIsDeletedFalseOrderByVersionNoDesc(gen.getUser().getId(), gen.getCourse().getId())
                .map(mg -> mg.getVersionNo() + 1)
                .orElse(1);
                
        com.lms.material.entity.MaterialGeneration newGen = new com.lms.material.entity.MaterialGeneration();
        newGen.setUser(gen.getUser());
        newGen.setCourse(gen.getCourse());
        newGen.setMaterialType(gen.getMaterialType());
        newGen.setLanguage(gen.getLanguage());
        newGen.setTitle(gen.getTitle() + " (V2)");
        newGen.setScopeType(gen.getScopeType());
        newGen.setScopeRefId(gen.getScopeRefId());
        newGen.setCustomLessonIds(gen.getCustomLessonIds());
        newGen.setQuantityLevel(gen.getQuantityLevel());
        newGen.setDifficultyLevel(gen.getDifficultyLevel());
        newGen.setVersionNo(nextVersion);
        newGen.setStatus(gen.getStatus());
        newGen.setParentGeneration(gen);
        
        materialGenerationRepository.save(newGen);
        
        Long newMaterialId = null;
        

        // Clone specific material
        if (gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            com.lms.material.entity.Quiz oldQuiz = quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(gen.getId()).orElse(null);
            if (oldQuiz != null) {
                com.lms.material.entity.Quiz newQuiz = new com.lms.material.entity.Quiz();
                newQuiz.setMaterialGeneration(newGen);
                newQuiz.setQuestionCount(oldQuiz.getQuestionCount());
                newQuiz.setIsOfficial(oldQuiz.getIsOfficial());
                newQuiz.setQuizType(oldQuiz.getQuizType());
                newQuiz.setAllowReview(oldQuiz.getAllowReview());
                newQuiz.setMaxAttempts(oldQuiz.getMaxAttempts());
                newQuiz.setDurationMinutes(oldQuiz.getDurationMinutes());
                quizRepository.save(newQuiz);
                newMaterialId = newQuiz.getId();
                
                // Deep clone QuizQuestions
                java.util.List<com.lms.material.entity.QuizQuestion> oldQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(oldQuiz.getId());
                for (com.lms.material.entity.QuizQuestion oldQ : oldQuestions) {
                    com.lms.material.entity.QuizQuestion newQ = new com.lms.material.entity.QuizQuestion();
                    newQ.setQuiz(newQuiz);
                    newQ.setContent(oldQ.getContent());
                    newQ.setIsMultipleChoice(oldQ.getIsMultipleChoice());
                    newQ.setDisplayOrder(oldQ.getDisplayOrder());
                    quizQuestionRepository.save(newQ);
                    
                    java.util.List<com.lms.material.entity.QuizOption> oldOptions = quizOptionRepository.findByQuizQuestion_Id(oldQ.getId());
                    for (com.lms.material.entity.QuizOption oldOpt : oldOptions) {
                        com.lms.material.entity.QuizOption newOpt = new com.lms.material.entity.QuizOption();
                        newOpt.setQuizQuestion(newQ);
                        newOpt.setContent(oldOpt.getContent());
                        newOpt.setIsCorrect(oldOpt.getIsCorrect());
                        quizOptionRepository.save(newOpt);
                    }
                }
            }
        } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            FlashcardDeck oldDeck = flashcardDeckRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
            if (oldDeck != null) {
                FlashcardDeck newDeck = new FlashcardDeck();
                newDeck.setMaterialGeneration(newGen);
                newDeck.setCardCount(oldDeck.getCardCount());
                newDeck.setIsOfficial(oldDeck.getIsOfficial());
                flashcardDeckRepository.save(newDeck);
                newMaterialId = newDeck.getId();
                
                // Deep clone Flashcards
                java.util.List<com.lms.material.entity.Flashcard> oldCards = flashcardRepository.findByFlashcardDeck_Id(oldDeck.getId());
                for (com.lms.material.entity.Flashcard oldCard : oldCards) {
                    com.lms.material.entity.Flashcard newCard = new com.lms.material.entity.Flashcard();
                    newCard.setFlashcardDeck(newDeck);
                    newCard.setFrontText(oldCard.getFrontText());
                    newCard.setBackText(oldCard.getBackText());
                    flashcardRepository.save(newCard);
                }
            }
        } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {

            Mindmap oldMindmap = mindmapRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
            if (oldMindmap != null) {
                Mindmap newMindmap = new Mindmap();
                newMindmap.setMaterialGeneration(newGen);
                newMindmap.setNodeCount(oldMindmap.getNodeCount());
                newMindmap.setMermaidCode(oldMindmap.getMermaidCode());
                newMindmap.setIsOfficial(oldMindmap.getIsOfficial());
                mindmapRepository.save(newMindmap);
                newMaterialId = newMindmap.getId();
            }
        }
        
        // 2. Set is_archived = true cho V1
        gen.setIsArchived(true);
        materialGenerationRepository.save(gen);
        
        // 3. Chuyển các assignment của V1 sang V2
        materialAssignmentService.transferAssignments(gen.getId(), newGen.getId());
        
        // Force the assignment to the target
        materialAssignmentService.assignMaterial(newGen.getId(), gen.getCourse().getId(), targetChapterId, targetLessonId);
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", newGen.getId());
        response.put("materialId", newMaterialId);
        response.put("message", "Đã tạo phiên bản mới và ghi đè thành công");
        
        return ResponseEntity.ok(response);
    }


    private final MindmapRepository mindmapRepository;
    private final com.lms.material.repository.QuizQuestionRepository quizQuestionRepository;
    private final com.lms.material.repository.QuizOptionRepository quizOptionRepository;
    private final com.lms.material.repository.FlashcardRepository flashcardRepository;
    private final com.lms.material.repository.MaterialFolderRepository materialFolderRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final com.lms.material.repository.MaterialGenerationRepository materialGenerationRepository;
    private final com.lms.material.repository.QuizRepository quizRepository;
    private final com.lms.material.repository.QuizAttemptRepository quizAttemptRepository;
    private final com.lms.material.repository.FlashcardReviewRepository flashcardReviewRepository;
    private final com.lms.material.service.MaterialAssignmentService materialAssignmentService;
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
        Long courseId = payload.get("courseId");
        
        materialAssignmentService.assignMaterial(id, courseId, chapterId, lessonId);
        
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

            Long folderId = gen.getFolder() != null ? gen.getFolder().getId() : null;
            java.util.List<java.util.Map<String, Object>> assignments = new java.util.ArrayList<>();
            if (gen.getAssignments() != null) {
                for (com.lms.material.entity.MaterialAssignment assignment : gen.getAssignments()) {
                    java.util.Map<String, Object> assignmentMap = new java.util.HashMap<>();
                    assignmentMap.put("id", assignment.getId());
                    if (assignment.getLesson() != null) {
                        assignmentMap.put("lessonId", assignment.getLesson().getId());
                    }
                    if (assignment.getChapter() != null) {
                        assignmentMap.put("chapterId", assignment.getChapter().getId());
                    }
                    if (assignment.getCourse() != null) {
                        assignmentMap.put("courseId", assignment.getCourse().getId());
                    }
                    assignments.add(assignmentMap);
                }
            }

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
    
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", gen.getId());
            map.put("materialType", gen.getMaterialType().name());
            map.put("title", gen.getTitle());
            map.put("createdAt", gen.getCreatedAt().toString());
            map.put("status", gen.getStatus().name());
            map.put("language", gen.getLanguage());
            map.put("versionNo", gen.getVersionNo());
            map.put("isOfficial", isOfficial);
            map.put("materialId", materialId);
            map.put("questionCount", questionCount);
            map.put("randomPickCount", randomPickCount);
            map.put("allowReview", allowReview);
            map.put("startTime", startTime != null ? startTime.toString() : null);
            map.put("endTime", endTime != null ? endTime.toString() : null);
            map.put("durationMinutes", durationMinutes);
            map.put("maxAttempts", maxAttempts);
            map.put("isProctored", isProctored);
            map.put("maxViolations", maxViolations);
            map.put("quizType", quizType);
            map.put("attemptCount", attemptCount);
            map.put("usageCount", usageCount);
            map.put("assignments", assignments);
            map.put("folderId", folderId);
            
            result.add(map);

            }
        }
        return ResponseEntity.ok(result);
    }
}
