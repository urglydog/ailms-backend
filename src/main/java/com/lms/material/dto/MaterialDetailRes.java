package com.lms.material.dto;

import com.lms.common.enums.GenStatus;
import com.lms.common.enums.MaterialType;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record MaterialDetailRes(
        Long id,
        MaterialType materialType,
        String language,
        String title,
        Integer versionNo,
        GenStatus status,
        LocalDateTime createdAt,
        String mermaidCode // Only populated for MINDMAP
) {
}
