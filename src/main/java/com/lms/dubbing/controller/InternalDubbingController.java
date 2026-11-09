package com.lms.dubbing.controller;

import com.lms.dubbing.dto.InternalDubbingDto.ChunkUpsertReq;
import com.lms.dubbing.dto.InternalDubbingDto.FinishReq;
import com.lms.dubbing.dto.InternalDubbingDto.JobContextRes;
import com.lms.dubbing.dto.InternalDubbingDto.StartReq;
import com.lms.dubbing.service.InternalDubbingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UC19 — callback nội bộ cho AI Worker (Python). Xác thực bằng
 * {@link com.lms.common.security.InternalApiTokenFilter}, KHÔNG phải JWT — không đặt
 * {@code @PreAuthorize} ở đây vì chuỗi filter {@code /api/internal/**} không gắn
 * {@code Authentication} người dùng nào.
 */
@RestController
@RequestMapping("/api/internal/dubbing/jobs/{jobId}")
@RequiredArgsConstructor
public class InternalDubbingController {

    private final InternalDubbingService internalDubbingService;

    @GetMapping("/context")
    public ResponseEntity<JobContextRes> getContext(@PathVariable Long jobId) {
        return ResponseEntity.ok(internalDubbingService.getContext(jobId));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable Long jobId, @Valid @RequestBody StartReq req) {
        internalDubbingService.start(jobId, req);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/chunks/{chunkIndex}")
    public ResponseEntity<Void> upsertChunk(
            @PathVariable Long jobId, @PathVariable Integer chunkIndex, @Valid @RequestBody ChunkUpsertReq req) {
        internalDubbingService.upsertChunk(jobId, chunkIndex, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/finish")
    public ResponseEntity<Void> finish(@PathVariable Long jobId, @Valid @RequestBody FinishReq req) {
        internalDubbingService.finish(jobId, req);
        return ResponseEntity.noContent().build();
    }
}
