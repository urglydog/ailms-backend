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
    private final com.lms.common.service.AiBillingService billingService;

    @GetMapping("/overview")
    public ResponseEntity<AiUsageOverviewService.Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String feature) {
        return ResponseEntity.ok(overviewService.overview(from, to, feature));
    }

    @GetMapping("/billing")
    public ResponseEntity<java.util.List<com.lms.common.service.AiBillingService.BillingRes>> billing() {
        return ResponseEntity.ok(billingService.list());
    }

    @org.springframework.web.bind.annotation.PostMapping("/billing")
    public ResponseEntity<com.lms.common.service.AiBillingService.BillingRes> addBilling(
            java.security.Principal principal, @org.springframework.web.bind.annotation.RequestBody com.lms.common.service.AiBillingService.CreateReq req) {
        return ResponseEntity.ok(billingService.create(principal.getName(), req));
    }
}
