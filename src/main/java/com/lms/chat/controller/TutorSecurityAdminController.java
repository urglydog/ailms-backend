package com.lms.chat.controller;

import com.lms.chat.dto.TutorSecurityDto.FlagRes;
import com.lms.chat.service.TutorSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BR-TUTOR-SEC-06 — Admin xem lại các tin nhắn học viên bị pre-check heuristic của Socratic
 * Tutor Agent gắn cờ (chỉ để theo dõi, KHÔNG có hành động khóa/chặn nào ở v1). */
@RestController
@RequestMapping("/api/v1/admin/tutor-security/flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TutorSecurityAdminController {

    private final TutorSecurityService tutorSecurityService;

    @GetMapping
    public ResponseEntity<Page<FlagRes>> getFlags(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(tutorSecurityService.getFlags(pageable));
    }
}
