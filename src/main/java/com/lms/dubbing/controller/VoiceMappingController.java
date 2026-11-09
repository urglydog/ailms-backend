package com.lms.dubbing.controller;

import com.lms.dubbing.dto.VoiceMappingDto.*;
import com.lms.dubbing.service.VoiceMappingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** UC47 — Admin cấu hình giọng đọc theo ngôn ngữ (BR-DUB-07: nguồn duy nhất quyết định ngôn ngữ khả dụng). */
@RestController
@RequestMapping("/api/v1/admin/voice-mappings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class VoiceMappingController {

    private final VoiceMappingService voiceMappingService;

    @GetMapping
    public ResponseEntity<List<Res>> getAll() {
        return ResponseEntity.ok(voiceMappingService.getAll());
    }

    @PostMapping
    public ResponseEntity<Res> create(@Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(voiceMappingService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Res> update(@PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(voiceMappingService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        voiceMappingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
