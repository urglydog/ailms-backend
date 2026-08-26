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
    private final com.lms.material.repository.QuizRepository quizRepository;
    private final com.lms.material.repository.QuizQuestionRepository quizQuestionRepository;
    private final com.lms.material.repository.QuizOptionRepository quizOptionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.material-queue:lms:material:jobs}")
    private String queueKey;

    @Transactional
    public MaterialGenerationRes requestGeneration(String email, MaterialGenerationReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));

        requireCourseAccess(user, course);

        // BR-MAT-08: Hạn ngạch 6 lần/ngày
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayCount = materialGenerationRepository.countByUser_IdAndCreatedAtGreaterThanEqual(user.getId(), startOfDay);
        if (todayCount >= 6) {
            throw new BusinessRuleViolationException("Đã đạt giới hạn sinh học liệu (6 lần/ngày) - BR-MAT-08");
        }

        // BR-MAT-07: Giới hạn 10 bộ / khóa học
        long courseCount = materialGenerationRepository.countByUser_IdAndCourse_Id(user.getId(), course.getId());
        if (courseCount >= 10) {
            throw new BusinessRuleViolationException("Đã đạt giới hạn sinh học liệu cho khóa học này (tối đa 10 bộ) - BR-MAT-07");
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

    public com.lms.material.dto.MaterialDetailRes getDetail(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        MaterialGeneration generation = materialGenerationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", id));
        
        if (!generation.isReusableBy(user)) {
            throw new AccessDeniedDomainException("Học liệu này thuộc về người khác");
        }
        
        String mermaidCode = null;
        java.util.List<com.lms.material.dto.MaterialDetailRes.FlashcardDto> flashcards = null;
        java.util.List<com.lms.material.dto.MaterialDetailRes.QuizQuestionDto> quizQuestions = null;
        
        if (generation.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
            mermaidCode = mindmapRepository.findByMaterialGeneration_Id(generation.getId())
                    .map(com.lms.material.entity.Mindmap::getMermaidCode)
                    .orElse(null);
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            java.util.Optional<com.lms.material.entity.FlashcardDeck> deck = flashcardDeckRepository.findByMaterialGeneration_Id(generation.getId());
            if (deck.isPresent()) {
                flashcards = flashcardRepository.findByFlashcardDeck_Id(deck.get().getId())
                        .stream()
                        .map(f -> com.lms.material.dto.MaterialDetailRes.FlashcardDto.builder()
                                .id(f.getId())
                                .frontText(f.getFrontText())
                                .backText(f.getBackText())
                                .build())
                        .toList();
            }
        } else if (generation.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            java.util.Optional<com.lms.material.entity.Quiz> quiz = quizRepository.findByMaterialGeneration_Id(generation.getId());
            if (quiz.isPresent()) {
                quizQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(quiz.get().getId())
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
}
