package com.lms.material.service;

import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import com.lms.common.enums.ScopeType;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.dubbing.entity.TranscriptSegment;
import com.lms.dubbing.repository.TranscriptSegmentRepository;
import com.lms.material.dto.InternalMaterialDto.FinishReq;
import com.lms.material.dto.InternalMaterialDto.GenerationContextRes;
import com.lms.material.dto.InternalMaterialDto.TranscriptSegmentDto;
import com.lms.material.dto.InternalMaterialDto.FlashcardDto;
import com.lms.material.dto.InternalMaterialDto.QuizDto;
import com.lms.material.entity.Flashcard;
import com.lms.material.entity.FlashcardDeck;
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.entity.Mindmap;
import com.lms.material.entity.Quiz;
import com.lms.material.entity.QuizOption;
import com.lms.material.entity.QuizQuestion;
import com.lms.material.repository.FlashcardDeckRepository;
import com.lms.material.repository.FlashcardRepository;
import com.lms.material.repository.MaterialGenerationRepository;
import com.lms.material.repository.MindmapRepository;
import com.lms.material.repository.QuizOptionRepository;
import com.lms.material.repository.QuizQuestionRepository;
import com.lms.material.repository.QuizRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalMaterialService {

    private final MaterialGenerationRepository materialGenerationRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final MindmapRepository mindmapRepository;
    private final FlashcardDeckRepository flashcardDeckRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final com.lms.common.repository.AiUsageLogRepository aiUsageLogRepository;

    @Transactional(readOnly = true)
    public GenerationContextRes getContext(Long generationId) {
        MaterialGeneration generation = materialGenerationRepository.findById(generationId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", generationId));

        List<TranscriptSegment> segments;
        if (generation.getScopeType() == ScopeType.WHOLE_COURSE) {
            segments = transcriptSegmentRepository.findByCourseIdAndIsSourceTrue(generation.getCourse().getId());
        } else if (generation.getScopeType() == ScopeType.CHAPTER) {
            segments = transcriptSegmentRepository.findByChapterIdAndIsSourceTrue(generation.getScopeRefId());
        } else if (generation.getScopeType() == ScopeType.CUSTOM_LESSONS && generation.getCustomLessonIds() != null && !generation.getCustomLessonIds().trim().isEmpty()) {
            List<Long> lessonIds = java.util.Arrays.stream(generation.getCustomLessonIds().replaceAll("[\\[\\]\"]", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
            segments = transcriptSegmentRepository.findByLessonIdInAndIsSourceTrue(lessonIds);
        } else {
            segments = List.of(); // Should not happen based on BR-MAT-01
        }

        List<TranscriptSegmentDto> transcriptDtos = segments.stream()
                .map(s -> TranscriptSegmentDto.builder()
                        .text(s.getText())
                        .startSec(s.getStartSec() != null ? s.getStartSec().doubleValue() : 0.0)
                        .endSec(s.getEndSec() != null ? s.getEndSec().doubleValue() : 0.0)
                        .build())
                .toList();

        return GenerationContextRes.builder()
                .generationId(generation.getId())
                .courseId(generation.getCourse().getId())
                .courseTitle(generation.getCourse().getTitle())
                .materialType(generation.getMaterialType().name())
                .language(generation.getLanguage())
                .scopeType(generation.getScopeType().name())
                .scopeRefId(generation.getScopeRefId())
                .quantityLevel(generation.getQuantityLevel() != null ? generation.getQuantityLevel().name() : null)
                .difficultyLevel(generation.getDifficultyLevel() != null ? generation.getDifficultyLevel().name() : null)
                .transcripts(transcriptDtos)
                .build();
    }

    @Transactional
    public void finish(Long generationId, FinishReq req) {
        MaterialGeneration generation = materialGenerationRepository.findById(generationId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", generationId));

        if ("COMPLETED".equals(req.outcome())) {
            generation.setStatus(GenStatus.COMPLETED);
            
            if (generation.getMaterialType() == MaterialType.MINDMAP) {
                Mindmap mindmap = new Mindmap();
                mindmap.setMaterialGeneration(generation);
                mindmap.setMermaidCode(req.mermaidCode());
                
                // Count basic nodes (just an approximation by counting newlines)
                int nodes = req.mermaidCode() != null ? req.mermaidCode().split("\n").length : 0;
                mindmap.setNodeCount(nodes);
                
                mindmapRepository.save(mindmap);
            } else if (generation.getMaterialType() == MaterialType.FLASHCARD && req.flashcards() != null) {
                FlashcardDeck deck = new FlashcardDeck();
                deck.setMaterialGeneration(generation);
                deck.setCardCount(req.flashcards().size());
                deck = flashcardDeckRepository.save(deck);
                
                for (FlashcardDto dto : req.flashcards()) {
                    Flashcard fc = new Flashcard();
                    fc.setFlashcardDeck(deck);
                    fc.setFrontText(dto.front_text());
                    fc.setBackText(dto.back_text());
                    flashcardRepository.save(fc);
                }
            } else if (generation.getMaterialType() == MaterialType.QUIZ && req.quizzes() != null) {
                Quiz quiz = new Quiz();
                quiz.setMaterialGeneration(generation);
                quiz.setQuestionCount(req.quizzes().size());
                quiz = quizRepository.save(quiz);
                
                int order = 1;
                for (QuizDto dto : req.quizzes()) {
                    QuizQuestion q = new QuizQuestion();
                    q.setQuiz(quiz);
                    q.setContent(dto.content());
                    q.setDisplayOrder(order++);
                    q = quizQuestionRepository.save(q);
                    
                    if (dto.options() != null) {
                        for (String optText : dto.options()) {
                            QuizOption opt = new QuizOption();
                            opt.setQuizQuestion(q);
                            opt.setContent(optText);
                            opt.setIsCorrect(optText.equals(dto.correct_answer()));
                            quizOptionRepository.save(opt);
                        }
                    }
                }
            }
        } else {
            generation.setStatus(GenStatus.FAILED);
            generation.setErrorMessage(req.errorMessage());
        }

        if (req.usageMetadata() != null) {
            com.lms.common.entity.AiUsageLog usageLog = new com.lms.common.entity.AiUsageLog();
            usageLog.setUserId(generation.getUser().getId());
            usageLog.setFeatureType(generation.getMaterialType().name());
            
            int promptTokens = req.usageMetadata().promptTokens() != null ? req.usageMetadata().promptTokens() : 0;
            int completionTokens = req.usageMetadata().completionTokens() != null ? req.usageMetadata().completionTokens() : 0;
            
            usageLog.setPromptTokens(promptTokens);
            usageLog.setCompletionTokens(completionTokens);
            usageLog.setTotalTokens(promptTokens + completionTokens);
            
            // Tạm tính cost: $1.5 / 1M prompt tokens, $2.0 / 1M completion tokens (Gemini Flash)
            double cost = (promptTokens / 1000000.0 * 1.5) + (completionTokens / 1000000.0 * 2.0);
            usageLog.setCostUsd(java.math.BigDecimal.valueOf(cost));
            
            aiUsageLogRepository.save(usageLog);
        }

        materialGenerationRepository.save(generation);
    }
}
