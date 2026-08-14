package com.lms.material.dto;

import java.util.List;
import lombok.Builder;

public class InternalMaterialDto {
    @Builder
    public record GenerationContextRes(
            Long generationId,
            Long courseId,
            String courseTitle,
            String materialType,
            String language,
            String scopeType,
            Long scopeRefId,
            String quantityLevel,
            String difficultyLevel,
            List<TranscriptSegmentDto> transcripts
    ) {}

    @Builder
    public record TranscriptSegmentDto(
            String text,
            Double startSec,
            Double endSec
    ) {}

    public record FinishReq(
            String outcome,
            String errorMessage,
            String mermaidCode
    ) {}
}
