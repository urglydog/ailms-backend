package com.lms.material.controller;

import com.lms.material.dto.MaterialGenerationReq;
import com.lms.material.dto.MaterialGenerationRes;
import com.lms.material.service.MaterialGenerationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialGenerationController {

    private final MaterialGenerationService materialGenerationService;

    @PostMapping
    public ResponseEntity<MaterialGenerationRes> requestGeneration(
            Principal principal,
            @Valid @RequestBody MaterialGenerationReq req) {
        MaterialGenerationRes res = materialGenerationService.requestGeneration(principal.getName(), req);
        // Task 11A — X-RateLimit-* dựa trên hạn ngạch SAU KHI request này đã được tính (BR-MAT-08).
        var quota = materialGenerationService.getQuotaStatus(principal.getName(), req.courseId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-RateLimit-Limit", String.valueOf(quota.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(quota.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(quota.resetEpochSeconds()))
                .body(res);
    }

    /** Task 11A — trạng thái hạn ngạch sinh học liệu hiện tại (để FE hiện "còn X lượt hôm nay"). */
    @GetMapping("/quota-status")
    public ResponseEntity<com.lms.material.service.MaterialGenerationService.MaterialQuotaRes> getQuotaStatus(
            Principal principal,
            @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(materialGenerationService.getQuotaStatus(principal.getName(), courseId));
    }

    @GetMapping
    public ResponseEntity<List<MaterialGenerationRes>> getGenerations(
            Principal principal,
            @RequestParam Long courseId) {
        return ResponseEntity.ok(materialGenerationService.getGenerations(principal.getName(), courseId));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<com.lms.material.dto.MaterialDetailRes> getDetail(
            Principal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(materialGenerationService.getDetail(principal.getName(), id));
    }

    @GetMapping("/available-languages")
    public ResponseEntity<List<com.lms.material.dto.LanguageAvailabilityRes>> getAvailableLanguages(@RequestParam Long courseId) {
        return ResponseEntity.ok(materialGenerationService.getAvailableLanguages(courseId));
    }

    @GetMapping("/course-chapters")
    public ResponseEntity<List<com.lms.catalog.dto.ChapterDto.Res>> getCourseChapters(
            @RequestParam Long courseId,
            @RequestParam(required = false) String language) {
        return ResponseEntity.ok(materialGenerationService.getCourseChapters(courseId, language));
    }

    @PatchMapping("/{id:\\d+}")
    public ResponseEntity<Void> updateMaterial(
            Principal principal,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String title = body.get("title");
        String mermaidCode = body.get("mermaidCode");
        materialGenerationService.updateMaterial(principal.getName(), id, title, mermaidCode);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> deleteMaterial(
            Principal principal,
            @PathVariable Long id) {
        materialGenerationService.deleteMaterial(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
