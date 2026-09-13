package com.lms.dubbing.dto;

import com.lms.dubbing.dto.InternalDubbingDto.SegmentDto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** DTO cho {@code /api/internal/transcripts/**} — callback báo kết quả job trích script gốc
 * lúc nạp video (xem {@code TranscriptExtractionService}), tách khỏi {@link InternalDubbingDto}
 * vì job này không gắn với 1 {@code AiJob} nào cả. */
public class InternalTranscriptDto {

    public record SourceReportReq(
            @NotBlank String outcome, // COMPLETED | FAILED | SKIPPED
            String detectedLanguage,
            List<SegmentDto> segments,
            String errorMessage
    ) {}
}
