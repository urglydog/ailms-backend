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
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(res);
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
    public ResponseEntity<List<String>> getAvailableLanguages(@RequestParam Long courseId) {
        return ResponseEntity.ok(materialGenerationService.getAvailableLanguages(courseId));
    }

    @GetMapping("/course-chapters")
    public ResponseEntity<List<com.lms.catalog.dto.ChapterDto.Res>> getCourseChapters(@RequestParam Long courseId) {
        return ResponseEntity.ok(materialGenerationService.getCourseChapters(courseId));
    }
}
