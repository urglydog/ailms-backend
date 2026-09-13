package com.lms.material.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.GenStatus;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.material.dto.MaterialGenerationReq;
import com.lms.material.dto.MaterialGenerationRes;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.repository.MaterialGenerationRepository;
import com.lms.material.dto.LanguageAvailabilityRes;
import com.lms.dubbing.repository.TranscriptRepository;
import com.lms.dubbing.repository.VoiceMappingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý sinh học liệu (Mindmap, Quiz, Flashcard).
 * Tích hợp AI Worker qua Redis Queue.
 */
@Service
@RequiredArgsConstructor
public class MaterialGenerationService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MaterialGenerationRepository materialGenerationRepository;
    private final com.lms.material.repository.MindmapRepository mindmapRepository;
    private final com.lms.material.repository.FlashcardDeckRepository flashcardDeckRepository;
    private final com.lms.material.repository.FlashcardRepository flashcardRepository;
    private final com.lms.material.repository.FlashcardReviewRepository flashcardReviewRepository;
    private final com.lms.material.repository.QuizRepository quizRepository;
    private final com.lms.material.repository.QuizQuestionRepository quizQuestionRepository;
    private final com.lms.material.repository.QuizOptionRepository quizOptionRepository;
    private final com.lms.material.repository.QuizAttemptRepository quizAttemptRepository;
    private final com.lms.catalog.repository.ChapterRepository chapterRepository;
    private final com.lms.catalog.repository.LessonRepository lessonRepository;
    private final TranscriptRepository transcriptRepository;
    private final VoiceMappingRepository voiceMappingRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.material-queue:lms:material:jobs}")
    private String queueKey;

    @Transactional
    public MaterialGenerationRes requestGeneration(String email, MaterialGenerationReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
                
        if (Boolean.TRUE.equals(user.getIsAiLocked())) {
            throw new AccessDeniedDomainException("Tai khoan cua ban da bi khoa tinh nang AI do vi pham chinh sach su dung.");
        }
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));

        requireCourseAccess(user, course);

        boolean isInstructor = course.getInstructor().getId().equals(user.getId());
        if (!isInstructor) {
            // BR-MAT-08: Hạn ngạch 6 lần/ngày (Chỉ áp dụng cho Học viên)
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            long todayCount = materialGenerationRepository.countByUser_IdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay);
            if (todayCount >= 6) {
                throw new BusinessRuleViolationException("Đã đạt giới hạn sinh học liệu (6 lần/ngày) - BR-MAT-08");
            }

            // BR-MAT-07: Giới hạn 10 bộ / khóa học (Chỉ áp dụng cho Học viên)
            long courseCount = materialGenerationRepository.countByUser_IdAndCourse_Id(user.getId(), course.getId());
            if (courseCount >= 10) {
                throw new BusinessRuleViolationException("Đã đạt giới hạn sinh học liệu cho khóa học này (tối đa 10 bộ) - BR-MAT-07");
            }
        }


        // BR-DUB-07 — ngôn ngữ đích phải nằm trong danh sách Admin đang bật, CÙNG nguồn với dropdown
        // lồng tiếng (UC47) để 2 nơi luôn khớp nhau. KHÔNG còn đòi hỏi "đã lồng tiếng ngôn ngữ này
        // chưa" như trước — BR-MAT-01 cho chọn ngôn ngữ đầu ra tự do.
        String reqLang = req.language();
        if (voiceMappingRepository.findByLanguageAndIsActiveTrue(reqLang).isEmpty()) {
            throw new BusinessRuleViolationException("Ngôn ngữ này chưa được hệ thống hỗ trợ (BR-DUB-07)");
        }

        // BR-MAT-01 — điều kiện DUY NHẤT còn lại: phạm vi được chọn phải có ít nhất 1 bài đã có
        // transcript gốc (WhisperX/Groq chạy ngay lúc nạp video — xem TranscriptExtractionService).
        if (!hasSourceTranscriptInScope(course.getId(), req)) {
            throw new BusinessRuleViolationException(
                    "Phạm vi này chưa có bài giảng nào sẵn sàng — transcript gốc có thể đang được "
                            + "trích xuất sau khi nạp video, vui lòng thử lại sau ít phút");
        }

        int nextVersion = materialGenerationRepository.findTopByUser_IdAndCourse_IdOrderByVersionNoDesc(user.getId(), course.getId())
                .map(mg -> mg.getVersionNo() + 1)
                .orElse(1);

        MaterialGeneration generation = new MaterialGeneration();
        generation.setUser(user);
        generation.setCourse(course);
        generation.setMaterialType(req.materialType());
        generation.setLanguage(req.language());
        generation.setScopeType(req.scopeType());
        generation.setScopeRefId(req.scopeRefId());
        if (req.scopeType() == com.lms.common.enums.ScopeType.CUSTOM_LESSONS && req.customLessonIds() != null && !req.customLessonIds().isEmpty()) {
            try {
                generation.setCustomLessonIds(objectMapper.writeValueAsString(req.customLessonIds()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Không serialize được customLessonIds", e);
            }
        }
        generation.setQuantityLevel(req.quantityLevel());
        generation.setDifficultyLevel(req.difficultyLevel());
        generation.setVersionNo(nextVersion);
        generation.setStatus(GenStatus.PENDING);

        MaterialGeneration saved = materialGenerationRepository.save(generation);

        String jsonPayload = toJson(saved);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisTemplate.opsForList().leftPush(queueKey, jsonPayload);
                    }
                }
        );

        return toDto(saved);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.lms.material.dto.MaterialDetailRes getDetail(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        MaterialGeneration generation = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        
        boolean isOfficial = false;
        
        String mermaidCode = null;
        java.util.List<com.lms.material.dto.MaterialDetailRes.FlashcardDto> flashcards = null;
        java.util.List<com.lms.material.dto.MaterialDetailRes.QuizQuestionDto> quizQuestions = null;
        
        if (generation.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
            java.util.Optional<com.lms.material.entity.Mindmap> mindmapOpt = mindmapRepository.findByMaterialGeneration_Id(generation.getId());
            if (mindmapOpt.isPresent()) {
                com.lms.material.entity.Mindmap mindmap = mindmapOpt.get();
                mermaidCode = mindmap.getMermaidCode();
                isOfficial = mindmap.getIsOfficial() != null ? mindmap.getIsOfficial() : false;
            }
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            java.util.Optional<com.lms.material.entity.FlashcardDeck> deckOpt = flashcardDeckRepository.findByMaterialGeneration_Id(generation.getId());
            if (deckOpt.isPresent()) {
                com.lms.material.entity.FlashcardDeck deck = deckOpt.get();
                isOfficial = deck.getIsOfficial() != null ? deck.getIsOfficial() : false;
                
                Long deckId = deck.getId();
                java.util.Map<Long, com.lms.material.entity.FlashcardReview> reviewMap = flashcardReviewRepository.findByUser_IdAndFlashcard_FlashcardDeck_Id(user.getId(), deckId)
                        .stream().collect(java.util.stream.Collectors.toMap(r -> r.getFlashcard().getId(), r -> r));

                flashcards = flashcardRepository.findByFlashcardDeck_Id(deckId)
                        .stream()
                        .map(card -> {
                            com.lms.material.entity.FlashcardReview r = reviewMap.get(card.getId());
                            if (r != null) {
                                boolean isDue = r.getNextReviewAt() == null || !r.getNextReviewAt().isAfter(java.time.LocalDate.now());
                                return com.lms.material.dto.MaterialDetailRes.FlashcardDto.builder()
                                        .id(card.getId())
                                        .frontText(card.getFrontText())
                                        .backText(card.getBackText())
                                        .nextReviewAt(r.getNextReviewAt())
                                        .intervalDays(r.getIntervalDays())
                                        .repetitions(r.getRepetitions())
                                        .easiness(r.getEasiness())
                                        .isDue(isDue)
                                        .build();
                            } else {
                                return com.lms.material.dto.MaterialDetailRes.FlashcardDto.builder()
                                        .id(card.getId())
                                        .frontText(card.getFrontText())
                                        .backText(card.getBackText())
                                        .nextReviewAt(java.time.LocalDate.now())
                                        .intervalDays(0)
                                        .repetitions(0)
                                        .easiness(new java.math.BigDecimal("2.50"))
                                        .isDue(true)
                                        .build();
                            }
                        })
                        .toList();
            }
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            java.util.Optional<com.lms.material.entity.Quiz> quizOpt = quizRepository.findByMaterialGeneration_Id(generation.getId());
            if (quizOpt.isPresent()) {
                com.lms.material.entity.Quiz quiz = quizOpt.get();
                isOfficial = quiz.getIsOfficial() != null ? quiz.getIsOfficial() : false;
                
                quizQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId())
                        .stream()
                        .map(q -> com.lms.material.dto.MaterialDetailRes.QuizQuestionDto.builder()
                                .id(q.getId())
                                .content(q.getContent())
                                .displayOrder(q.getDisplayOrder())
                                .options(quizOptionRepository.findByQuizQuestion_Id(q.getId())
                                        .stream()
                                        .map(o -> com.lms.material.dto.MaterialDetailRes.QuizOptionDto.builder()
                                                .id(o.getId())
                                                .content(o.getContent())
                                                .isCorrect(o.getIsCorrect())
                                                .build())
                                        .toList())
                                .build())
                        .toList();
            }
        }
        
        if (!generation.isReusableBy(user) && !isOfficial) {
            throw new AccessDeniedDomainException("Học liệu này thuộc về người khác");
        }
        
        return com.lms.material.dto.MaterialDetailRes.builder()
                .id(generation.getId())
                .materialType(generation.getMaterialType())
                .language(generation.getLanguage())
                .title(generation.getTitle())
                .versionNo(generation.getVersionNo())
                .status(generation.getStatus())
                .createdAt(generation.getCreatedAt())
                .mermaidCode(mermaidCode)
                .flashcards(flashcards)
                .quizQuestions(quizQuestions)
                .build();
    }

    private void requireCourseAccess(User user, Course course) {
        boolean isInstructor = course.getInstructor().getId().equals(user.getId());
        if (isInstructor) {
            return;
        }
        boolean enrolled = enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId());
        if (!enrolled) {
            throw new AccessDeniedDomainException("Bạn chưa sở hữu khóa học này");
        }
    }

    private String toJson(MaterialGeneration generation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationId", generation.getId());
        payload.put("courseId", generation.getCourse().getId());
        payload.put("materialType", generation.getMaterialType().name());
        
        if (generation.getScopeType() == com.lms.common.enums.ScopeType.CUSTOM_LESSONS && generation.getCustomLessonIds() != null) {
            try {
                payload.put("customLessonIds", objectMapper.readValue(generation.getCustomLessonIds(), java.util.List.class));
            } catch (JsonProcessingException e) {
                // ignore
            }
        }
        
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được payload hàng đợi sinh học liệu", e);
        }
    }

    public java.util.List<MaterialGenerationRes> getGenerations(String email, Long courseId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        requireCourseAccess(user, course);
        
        return materialGenerationRepository.findByUser_IdAndCourse_IdOrderByVersionNoDesc(user.getId(), courseId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void updateMaterial(String email, Long id, String title, String mermaidCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        MaterialGeneration generation = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!generation.isReusableBy(user)) {
            throw new AccessDeniedDomainException("Học liệu này thuộc về người khác");
        }
        if (title != null) {
            generation.setTitle(title);
        }
        if (mermaidCode != null && generation.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
            mindmapRepository.findByMaterialGeneration_Id(generation.getId()).ifPresent(mindmap -> {
                mindmap.setMermaidCode(mermaidCode);
                mindmapRepository.save(mindmap);
            });
        }
        materialGenerationRepository.save(generation);
    }

    @Transactional
    public void deleteMaterial(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        MaterialGeneration generation = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        if (!generation.isReusableBy(user)) {
            throw new AccessDeniedDomainException("Học liệu này thuộc về người khác");
        }

        if (generation.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
            mindmapRepository.findByMaterialGeneration_Id(generation.getId()).ifPresent(mindmapRepository::delete);
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            flashcardDeckRepository.findByMaterialGeneration_Id(generation.getId()).ifPresent(deck -> {
                flashcardReviewRepository.deleteByFlashcard_FlashcardDeck_Id(deck.getId());
                flashcardRepository.deleteByFlashcardDeck_Id(deck.getId());
                flashcardDeckRepository.delete(deck);
            });
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            quizRepository.findByMaterialGeneration_Id(generation.getId()).ifPresent(quiz -> {
                quizAttemptRepository.deleteByQuiz_Id(quiz.getId());
                
                quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.getId()).forEach(question -> {
                    quizOptionRepository.deleteByQuizQuestion_Id(question.getId());
                });
                quizQuestionRepository.deleteByQuiz_Id(quiz.getId());
                quizRepository.delete(quiz);
            });
        }
        
        materialGenerationRepository.delete(generation);
    }

    public MaterialGenerationRes toDto(MaterialGeneration generation) {
        return MaterialGenerationRes.builder()
                .id(generation.getId())
                .materialType(generation.getMaterialType())
                .language(generation.getLanguage())
                .title(generation.getTitle())
                .versionNo(generation.getVersionNo())
                .status(generation.getStatus())
                .celeryTaskId(generation.getCeleryTaskId())
                .createdAt(generation.getCreatedAt())
                .updatedAt(generation.getUpdatedAt())
                .build();
    }

    /** UC24 — danh sách ngôn ngữ cho FE chọn, CÙNG nguồn với dropdown lồng tiếng (BR-DUB-07),
     * kèm gợi ý "đã có bản dịch sẵn" (dấu tích) hay chưa (dấu chấm) — xem {@link LanguageAvailabilityRes}. */
    public java.util.List<LanguageAvailabilityRes> getAvailableLanguages(Long courseId) {
        java.util.Set<String> translated = new java.util.HashSet<>(
                transcriptRepository.findTranslatedLanguagesByCourseId(courseId));
        return voiceMappingRepository.findByIsActiveTrue().stream()
                .map(com.lms.dubbing.entity.VoiceMapping::getLanguage)
                .distinct()
                .map(code -> new LanguageAvailabilityRes(code, displayLabel(code), translated.contains(code)))
                .toList();
    }

    private String displayLabel(String languageCode) {
        return java.util.Locale.forLanguageTag(languageCode).getDisplayName(java.util.Locale.forLanguageTag("vi-VN"));
    }

    /** BR-MAT-01 — phạm vi được chọn phải có ít nhất 1 bài đã có transcript gốc. */
    private boolean hasSourceTranscriptInScope(Long courseId, com.lms.material.dto.MaterialGenerationReq req) {
        return switch (req.scopeType()) {
            case WHOLE_COURSE -> transcriptRepository.existsSourceTranscriptByCourseId(courseId);
            case CHAPTER -> req.scopeRefId() != null && transcriptRepository.existsSourceTranscriptByChapterId(req.scopeRefId());
            case CUSTOM_LESSONS -> req.customLessonIds() != null && !req.customLessonIds().isEmpty()
                    && transcriptRepository.existsSourceTranscriptByLessonIdIn(req.customLessonIds());
            default -> false;
        };
    }

    /** UC24 — chọn phạm vi Chương/Bài tuỳ chọn: chỉ hiện bài ĐÃ có transcript gốc (sẵn sàng sinh
     * học liệu ở BẤT KỲ ngôn ngữ nào — BR-MAT-01), không còn lọc theo "đã lồng tiếng ngôn ngữ X". */
    public java.util.List<com.lms.catalog.dto.ChapterDto.Res> getCourseChapters(Long courseId, String language) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }

        java.util.Set<Long> readyLessonIds = new java.util.HashSet<>(
                transcriptRepository.findLessonIdsWithSourceTranscriptByCourseId(courseId));

        return chapterRepository.findByCourseIdOrderByDisplayOrderAsc(courseId).stream()
                .map(chapter -> {
                    java.util.List<com.lms.catalog.dto.LessonDto.Res> filteredLessons = lessonRepository.findByChapterIdOrderByDisplayOrderAsc(chapter.getId()).stream()
                            .filter(lesson -> readyLessonIds.contains(lesson.getId()))
                            .map(lesson -> new com.lms.catalog.dto.LessonDto.Res(
                                    lesson.getId(),
                                    lesson.getTitle(),
                                    lesson.getDisplayOrder(),
                                    lesson.getIsPreview(),
                                    lesson.getStatus(),
                                    lesson.getVideoSource(),
                                    lesson.getVideoUrl(),
                                    lesson.getYoutubeId(),
                                    lesson.getDurationSec()
                            ))
                            .toList();
                    return new com.lms.catalog.dto.ChapterDto.Res(
                            chapter.getId(),
                            chapter.getTitle(),
                            chapter.getDisplayOrder(),
                            filteredLessons
                    );
                })
                .filter(chapter -> !chapter.lessons().isEmpty())
                .toList();
    }
}
