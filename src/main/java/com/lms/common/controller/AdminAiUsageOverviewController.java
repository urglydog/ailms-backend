package com.lms.common.controller;

import com.lms.common.service.AiUsageOverviewService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tổng quan chi phí AI (admin). */
@RestController
@RequestMapping("/api/v1/admin/ai-usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiUsageOverviewController {

    private final AiUsageOverviewService overviewService;

    @GetMapping("/overview")
    public ResponseEntity<AiUsageOverviewService.Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String feature) {
        return ResponseEntity.ok(overviewService.overview(from, to, feature));
    }
}
