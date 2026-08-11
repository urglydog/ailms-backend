package com.lms.dubbing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO cho {@code /api/internal/dubbing/**} — hợp đồng HTTP giữa AI Worker (Python) và
 * backend. AI Worker KHÔNG kết nối MySQL trực tiếp (xem {@code architecture.md}), nên đây
 * là kênh DUY NHẤT để pipeline lồng tiếng (F5.2) ghi kết quả.
 */
public class InternalDubbingDto {

    public record SegmentDto(
            @NotNull Integer seq,
            @NotNull BigDecimal startSec,
            @NotNull BigDecimal endSec,
            @NotBlank String text,
            /** Chỉ có ở targetSegments — hệ số R đã áp dụng (BR-DUB-03). */
            BigDecimal speechRate,
            Boolean wasSummarized
    ) {}

    /** UC19 bước ⑧–⑪ — ngữ cảnh AI Worker cần để bắt đầu pipeline cho một job. */
    public record JobContextRes(
            Long jobId,
            Long lessonId,
            String videoSource,
            String videoUrl,
            Integer durationSec,
            String sourceLanguage,
            String targetLanguage,
            /** Giọng đọc đã chọn theo BR-DUB-07 — AI Worker KHÔNG được tự chọn/hardcode. */
            String voiceName,
            /** BR-DUB-01 — true nếu bài học đã có bản ghi âm gốc, AI Worker bỏ qua bước ASR. */
            boolean sourceTranscriptAvailable,
            List<SegmentDto> sourceSegments
    ) {}

    public record StartReq(
            @NotNull Integer totalChunks,
            String celeryTaskId
    ) {}

    /** UC19 bước ⑮–⑯ — một phân đoạn 10 phút (BR-CHUNK-02) đã xử lý xong hoặc lỗi. */
    public record ChunkUpsertReq(
            @NotBlank String status, // COMPLETED | FAILED
            @NotNull Integer startSec,
            @NotNull Integer endSec,
            String audioFileUrl,
            /**
             * Chỉ gửi ở job ĐẦU TIÊN của bài học (BR-DUB-01 "lần đầu điền sourceLanguage") —
             * Groq ASR tự phát hiện, bắt buộc đi kèm {@link #sourceSegments}.
             */
            String detectedSourceLanguage,
            List<SegmentDto> sourceSegments,
            List<SegmentDto> targetSegments,
            String errorMessage
    ) {}

    public record FinishReq(
            @NotBlank String outcome, // COMPLETED | FAILED | SKIPPED
            String errorMessage,
            String finalUrl,
            Integer durationSec,
            Long fileSize
    ) {}
}
