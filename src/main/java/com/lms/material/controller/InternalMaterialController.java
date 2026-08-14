package com.lms.material.controller;

import com.lms.material.dto.InternalMaterialDto.FinishReq;
import com.lms.material.dto.InternalMaterialDto.GenerationContextRes;
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
@RequestMapping("/api/internal/materials/{generationId}")
@RequiredArgsConstructor
public class InternalMaterialController {

    private final InternalMaterialService internalMaterialService;

    @GetMapping("/context")
    public ResponseEntity<GenerationContextRes> getContext(@PathVariable Long generationId) {
        return ResponseEntity.ok(internalMaterialService.getContext(generationId));
    }

    @PostMapping("/finish")
    public ResponseEntity<Void> finish(@PathVariable Long generationId, @RequestBody FinishReq req) {
        internalMaterialService.finish(generationId, req);
        return ResponseEntity.noContent().build();
    }
}
