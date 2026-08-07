package com.lms.catalog.dto;

public class LessonDocumentDto {

    public record Res(
            Long id,
            String fileName,
            String fileUrl,
            String fileType,
            Long fileSize
    ) {}
}
