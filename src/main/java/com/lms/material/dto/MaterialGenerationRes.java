package com.lms.material.dto;

import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record MaterialGenerationRes(
        Long id,
        MaterialType materialType,
        String language,
        String title,
        Integer versionNo,
        GenStatus status,
        String celeryTaskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
