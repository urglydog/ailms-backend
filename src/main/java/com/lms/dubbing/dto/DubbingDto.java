package com.lms.dubbing.dto;

import jakarta.validation.constraints.NotBlank;

public class DubbingDto {

    /** UC18 — học viên/giảng viên chọn ngôn ngữ đích để lồng tiếng. */
    public record RequestReq(
            @NotBlank(message = "Ngôn ngữ đích không được để trống")
            String targetLanguage
    ) {}

    /**
     * {@code status}:
     * <ul>
     *   <li>{@code CREATED} — vừa tạo {@code AiJob} mới, FE subscribe {@code /topic/dubbing/{lessonId}}</li>
     *   <li>{@code PROCESSING} — đã có job đang chạy (người khác bấm trước, BR-DUB-05), chỉ subscribe</li>
     *   <li>{@code AVAILABLE} — đã có {@code AudioTrack} COMPLETED (BR-DUB-04), phát {@code audioUrl} luôn</li>
     * </ul>
     */
    public record Res(
            String status,
            Long jobId,
            String audioUrl
    ) {}
}
