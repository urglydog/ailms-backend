package com.lms.material.dto;

import com.lms.common.enums.DifficultyLevel;
import com.lms.common.enums.MaterialType;
import com.lms.common.enums.QuantityLevel;
import com.lms.common.enums.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record MaterialGenerationReq(
        @NotNull(message = "courseId không được để trống") Long courseId,
        @NotNull(message = "materialType không được để trống") MaterialType materialType,
        @NotBlank(message = "language không được để trống") String language,
        @NotNull(message = "scopeType không được để trống") ScopeType scopeType,
        Long scopeRefId,
        java.util.List<Long> customLessonIds,
        QuantityLevel quantityLevel,
        DifficultyLevel difficultyLevel
) {
}
