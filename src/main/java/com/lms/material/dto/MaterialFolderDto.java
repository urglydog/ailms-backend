package com.lms.material.dto;

import java.time.LocalDateTime;

public record MaterialFolderDto(
        Long id,
        String name,
        Long courseId,
        Long parentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
