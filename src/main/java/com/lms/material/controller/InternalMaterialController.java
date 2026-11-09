package com.lms.material.controller;

import com.lms.material.dto.InternalMaterialDto.FinishReq;
import com.lms.material.dto.InternalMaterialDto.GenerationContextRes;
import com.lms.material.dto.InternalMaterialDto.SaveTranslatedSegmentsReq;
import com.lms.material.service.InternalMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/materials")
@RequiredArgsConstructor
public class InternalMaterialController {

    private final InternalMaterialService internalMaterialService;

    @GetMapping("/{generationId}/context")
    public ResponseEntity<GenerationContextRes> getContext(@PathVariable Long generationId) {
        return ResponseEntity.ok(internalMaterialService.getContext(generationId));
    }

    @PostMapping("/{generationId}/finish")
    public ResponseEntity<Void> finish(@PathVariable Long generationId, @RequestBody FinishReq req) {
        internalMaterialService.finish(generationId, req);
        return ResponseEntity.noContent().build();
    }

    /** UC24/25 — AI Worker tự dịch cho 1 bài chưa có bản dịch ngôn ngữ đích rồi báo lại đây để
     * lưu, tái sử dụng được cho lần lồng tiếng ngôn ngữ đó sau này. */
    @PostMapping("/lessons/{lessonId}/translated-segments")
    public ResponseEntity<Void> saveTranslatedSegments(
            @PathVariable Long lessonId, @RequestBody SaveTranslatedSegmentsReq req) {
        internalMaterialService.saveTranslatedSegments(lessonId, req.language(), req.segments());
        return ResponseEntity.noContent().build();
    }
}
