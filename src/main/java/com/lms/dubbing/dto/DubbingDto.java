package com.lms.dubbing.dto;

import jakarta.validation.constraints.NotBlank;

public class DubbingDto {

    /**
     * UC18 — học viên/giảng viên chọn ngôn ngữ đích để lồng tiếng.
     *
     * {@code voiceName}: tuỳ chọn (UC20 mở rộng) — tên giọng đọc trong {@code voice_mappings}
     * đang active của đúng {@code targetLanguage}. Để trống thì backend tự lấy giọng
     * {@code isDefault} như hành vi cũ (BR-DUB-07). Chỉ có tác dụng khi đây là job MỚI — nếu
     * (bài học, ngôn ngữ) đã có job đang chạy hoặc audio hoàn chỉnh (BR-DUB-04/05), giọng của
     * job/audio đó đã được người kích hoạt TRƯỚC quyết định, giá trị gửi lên bị bỏ qua.
     */
    public record RequestReq(
            @NotBlank(message = "Ngôn ngữ đích không được để trống")
            String targetLanguage,
            String voiceName
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

    /** UC20 mở rộng — 1 giọng đọc khả dụng của 1 ngôn ngữ, cho FE dựng ô chọn ngôn ngữ + giọng. */
    public record VoiceOptionRes(
            String language,
            String voiceName,
            String gender,
            boolean isDefault
    ) {}
}
