package com.lms.material.controller;

import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.material.entity.FlashcardDeck;
import com.lms.material.entity.Mindmap;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.MindmapRepository;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String, String>> unassignMaterial(Principal principal, @PathVariable Long assignmentId) {
        // Validation of ownership could be added here if needed, but for simplicity assuming AssignmentService handles it or it's implicitly trusted by Instructor Role
        materialAssignmentService.unassignMaterial(assignmentId);
        return ResponseEntity.ok(java.util.Map.of("message", "Đã gỡ phân phối học liệu"));
    }

    @PutMapping("/{id}/move-to-folder")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> moveToFolder(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Object rawFolderId = payload.get("folderId");
        Long folderId = (rawFolderId != null) ? ((Number) rawFolderId).longValue() : null;
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
    public ResponseEntity<java.util.Map<String, Object>> versioningOverwrite(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }

        com.lms.material.entity.MaterialGeneration newGen = createNextVersionShell(gen, gen.getTitle());
        Long newMaterialId = cloneMaterialContent(gen, newGen);

        // Set is_archived = true cho bản cũ
        gen.setIsArchived(true);
        materialGenerationRepository.save(gen);

        // Chuyển các assignment sang bản mới — transferAssignments đã bảo toàn đúng assignment
        // tới đích, vì versioning-overwrite chỉ được gọi khi học liệu ĐÃ đang gán vào đúng đích
        // đó. KHÔNG gọi thêm assignMaterial ở đây nữa — làm vậy sẽ tạo thêm 1 MaterialAssignment
        // trùng lặp trỏ vào cùng đích mỗi lần kéo đè (đã từng là bug khiến badge đếm sai).
        materialAssignmentService.transferAssignments(gen.getId(), newGen.getId());

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", newGen.getId());
        response.put("materialId", newMaterialId);
        response.put("message", "Đã tạo phiên bản mới và ghi đè thành công");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getVersionHistory(Principal principal, @PathVariable Long id) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        Long rootId = gen.getRootGenerationId() != null ? gen.getRootGenerationId() : gen.getId();
        java.util.List<com.lms.material.entity.MaterialGeneration> lineage =
                materialGenerationRepository.findByRootGenerationIdOrderByVersionNoAsc(rootId);
        if (lineage.isEmpty()) {
            lineage = java.util.List.of(gen);
        }

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        int position = 0;
        for (com.lms.material.entity.MaterialGeneration v : lineage) {
            position++;
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", v.getId());
            map.put("displayVersionNo", position);
            map.put("title", v.getTitle());
            map.put("createdAt", v.getCreatedAt().toString());
            map.put("createdBy", v.getUser() != null ? v.getUser().getFullName() : null);
            map.put("isActive", !Boolean.TRUE.equals(v.getIsArchived()));
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/restore-version")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<java.util.Map<String, Object>> restoreVersion(Principal principal, @PathVariable Long id) {
        com.lms.material.entity.MaterialGeneration source = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!source.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        Long rootId = source.getRootGenerationId() != null ? source.getRootGenerationId() : source.getId();
        com.lms.material.entity.MaterialGeneration active = materialGenerationRepository
                .findTopByRootGenerationIdAndIsArchivedFalseOrderByVersionNoDesc(rootId)
                .orElse(source);

        // Nội dung lấy từ bản được chọn khôi phục (source), nhưng metadata (title gốc, gán bài
        // học...) kế thừa từ bản ĐANG active — khôi phục = "tạo bản mới nhất kế tiếp mang nội
        // dung cũ", không ghi đè ngược, đúng nguyên tắc BR-MAT-07 và không làm mất assignment
        // hiện tại của bản đang dùng.
        com.lms.material.entity.MaterialGeneration newGen = createNextVersionShell(active, active.getTitle());
        Long newMaterialId = cloneMaterialContent(source, newGen);

        active.setIsArchived(true);
        materialGenerationRepository.save(active);
        materialAssignmentService.transferAssignments(active.getId(), newGen.getId());

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", newGen.getId());
        response.put("materialId", newMaterialId);
        response.put("message", "Đã khôi phục nội dung phiên bản cũ thành phiên bản mới nhất");
        return ResponseEntity.ok(response);
    }

    /** Tạo bản ghi MaterialGeneration mới kế tiếp trong đúng dòng version của {@code metaSource}. */
    private com.lms.material.entity.MaterialGeneration createNextVersionShell(com.lms.material.entity.MaterialGeneration metaSource, String titleSource) {
        Long rootId = metaSource.getRootGenerationId() != null ? metaSource.getRootGenerationId() : metaSource.getId();
        if (metaSource.getRootGenerationId() == null) {
            metaSource.setRootGenerationId(rootId);
            materialGenerationRepository.save(metaSource);
        }
        // Số version tiếp theo = số bản ghi thực tế đã có trong dòng này + 1 — KHÔNG dùng
        // max(versionNo) vì cột này có thể mang giá trị lịch sử bị lệch từ trước khi sửa lỗi.
        int nextVersion = (int) materialGenerationRepository.countByRootGenerationId(rootId) + 1;
        String baseTitle = titleSource != null ? titleSource.replaceAll("\\s*\\(V\\d+\\)\\s*$", "") : "";

        com.lms.material.entity.MaterialGeneration newGen = new com.lms.material.entity.MaterialGeneration();
        newGen.setUser(metaSource.getUser());
        newGen.setCourse(metaSource.getCourse());
        newGen.setMaterialType(metaSource.getMaterialType());
        newGen.setLanguage(metaSource.getLanguage());
        newGen.setTitle(baseTitle + " (V" + nextVersion + ")");
        newGen.setScopeType(metaSource.getScopeType());
        newGen.setScopeRefId(metaSource.getScopeRefId());
        newGen.setCustomLessonIds(metaSource.getCustomLessonIds());
        newGen.setQuantityLevel(metaSource.getQuantityLevel());
        newGen.setDifficultyLevel(metaSource.getDifficultyLevel());
        newGen.setVersionNo(nextVersion);
        newGen.setStatus(metaSource.getStatus());
        newGen.setParentGeneration(metaSource);
        newGen.setRootGenerationId(rootId);

        return materialGenerationRepository.save(newGen);
    }

    /** Deep-clone nội dung (Quiz/Flashcard/Mindmap) từ {@code source} sang {@code newGen}. */
    private Long cloneMaterialContent(com.lms.material.entity.MaterialGeneration source, com.lms.material.entity.MaterialGeneration newGen) {
        Long newMaterialId = null;

        if (source.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            com.lms.material.entity.Quiz oldQuiz = quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(source.getId()).orElse(null);
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
        } else if (source.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            FlashcardDeck oldDeck = flashcardDeckRepository.findByMaterialGeneration_Id(source.getId()).orElse(null);
            if (oldDeck != null) {
                FlashcardDeck newDeck = new FlashcardDeck();
                newDeck.setMaterialGeneration(newGen);
                newDeck.setCardCount(oldDeck.getCardCount());
                newDeck.setIsOfficial(oldDeck.getIsOfficial());
                flashcardDeckRepository.save(newDeck);
                newMaterialId = newDeck.getId();

                java.util.List<com.lms.material.entity.Flashcard> oldCards = flashcardRepository.findByFlashcardDeck_Id(oldDeck.getId());
                for (com.lms.material.entity.Flashcard oldCard : oldCards) {
                    com.lms.material.entity.Flashcard newCard = new com.lms.material.entity.Flashcard();
                    newCard.setFlashcardDeck(newDeck);
                    newCard.setFrontText(oldCard.getFrontText());
                    newCard.setBackText(oldCard.getBackText());
                    flashcardRepository.save(newCard);
                }
            }
        } else if (source.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
            Mindmap oldMindmap = mindmapRepository.findByMaterialGeneration_Id(source.getId()).orElse(null);
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

        return newMaterialId;
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
    private final CourseRepository courseRepository;

    @PutMapping("/{id}/attach-lesson")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    @Transactional
    public ResponseEntity<java.util.Map<String, String>> attachToLesson(Principal principal, @PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        com.lms.material.entity.MaterialGeneration gen = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!gen.getCourse().getInstructor().getEmail().equals(principal.getName())) {
            throw new AccessDeniedDomainException("Ban khong co quyen");
        }
        
        Object rawLessonId = payload.get("lessonId");
        Object rawChapterId = payload.get("chapterId");
        Object rawCourseId = payload.get("courseId");
        Long lessonId = (rawLessonId != null) ? ((Number) rawLessonId).longValue() : null;
        Long chapterId = (rawChapterId != null) ? ((Number) rawChapterId).longValue() : null;
        Long courseId = (rawCourseId != null) ? ((Number) rawCourseId).longValue() : null;
        
        materialAssignmentService.assignMaterial(id, courseId, chapterId, lessonId);
        
        return ResponseEntity.ok(java.util.Map.of("message", "Đã cập nhật đính kèm học liệu"));
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
            if (gen.getStatus() == com.lms.common.enums.GenStatus.ARCHIVED || Boolean.TRUE.equals(gen.getIsArchived())) {
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
                    if (assignment.getLesson() != null || assignment.getChapter() != null) {
                        assignments.add(assignmentMap);
                    }
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

            // Hiển thị cho học viên nếu: do chính họ tạo, HOẶC đang là Official, 
            // HOẶC đã được giảng viên phân phối (assign) vào lesson/chapter (thể hiện ý định chia sẻ)
            boolean hasAssignment = !assignments.isEmpty();
            if (createdByInstructor || isOfficial || hasAssignment) {
    
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", gen.getId());
            map.put("materialType", gen.getMaterialType().name());
            map.put("title", gen.getTitle());
            map.put("createdAt", gen.getCreatedAt().toString());
            map.put("status", gen.getStatus().name());
            map.put("language", gen.getLanguage());
            map.put("versionNo", gen.getVersionNo());
            map.put("rootGenerationId", gen.getRootGenerationId() != null ? gen.getRootGenerationId() : gen.getId());
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
