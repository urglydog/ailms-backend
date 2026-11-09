package com.lms.dubbing.controller;

import com.lms.dubbing.dto.InternalTranscriptDto.SourceReportReq;
import com.lms.dubbing.service.TranscriptExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Callback nội bộ cho AI Worker báo kết quả job trích script gốc lúc nạp video — xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter}, KHÔNG phải JWT, giống toàn bộ
 * {@code /api/internal/**} khác.
 */
@RestController
@RequestMapping("/api/internal/transcripts/lessons/{lessonId}")
@RequiredArgsConstructor
public class InternalTranscriptController {

    private final TranscriptExtractionService transcriptExtractionService;

    @PostMapping("/source")
    public ResponseEntity<Void> reportSource(@PathVariable Long lessonId, @Valid @RequestBody SourceReportReq req) {
        transcriptExtractionService.reportResult(
                lessonId, req.outcome(), req.detectedLanguage(), req.segments(), req.errorMessage());
        return ResponseEntity.noContent().build();
    }
}
