package com.lms.catalog.dto;

public class LessonDocumentDto {

    /** "Tài nguyên" kiểu dán link (15/09/2026, mở rộng — giao diện tham khảo Udemy "+ Tài
     * nguyên" > "Dán link") — KHÁC upload file thật, chỉ lưu lại đường dẫn học viên bấm vào. */
    public record AddLinkReq(
            @jakarta.validation.constraints.NotBlank String title,
            @jakarta.validation.constraints.NotBlank String url
    ) {}

    public record Res(
            Long id,
            String fileName,
            String fileUrl,
            String fileType,
            Long fileSize
    ) {}
}
