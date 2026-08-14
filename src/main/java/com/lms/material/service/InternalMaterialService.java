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
import com.lms.material.entity.MaterialGeneration;
import com.lms.material.entity.Mindmap;
import com.lms.material.repository.MaterialGenerationRepository;
import com.lms.material.repository.MindmapRepository;
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

    @Transactional(readOnly = true)
    public GenerationContextRes getContext(Long generationId) {
        MaterialGeneration generation = materialGenerationRepository.findById(generationId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialGeneration", generationId));

        List<TranscriptSegment> segments;
        if (generation.getScopeType() == ScopeType.WHOLE_COURSE) {
            segments = transcriptSegmentRepository.findByCourseIdAndIsSourceTrue(generation.getCourse().getId());
        } else if (generation.getScopeType() == ScopeType.CHAPTER) {
            segments = transcriptSegmentRepository.findByChapterIdAndIsSourceTrue(generation.getScopeRefId());
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
            }
            // Add Flashcard and Quiz handling in subsequent steps
        } else {
            generation.setStatus(GenStatus.FAILED);
            generation.setErrorMessage(req.errorMessage());
        }

        materialGenerationRepository.save(generation);
    }
}
