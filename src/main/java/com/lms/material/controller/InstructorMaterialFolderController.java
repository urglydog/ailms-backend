package com.lms.material.controller;

import com.lms.material.dto.MaterialFolderDto;
import com.lms.material.dto.MaterialFolderReq;
import com.lms.material.service.MaterialFolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor/material-folders")
@RequiredArgsConstructor
public class InstructorMaterialFolderController {

    private final MaterialFolderService materialFolderService;

    @GetMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MaterialFolderDto>> getFolders(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(materialFolderService.getFoldersByCourse(courseId, parentId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MaterialFolderDto> createFolder(Principal principal, @Valid @RequestBody MaterialFolderReq req) {
        return ResponseEntity.ok(materialFolderService.createFolder(req, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MaterialFolderDto> updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody MaterialFolderReq req) {
        return ResponseEntity.ok(materialFolderService.updateFolder(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteFolder(Principal principal, @PathVariable Long id) {
        materialFolderService.deleteFolder(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
