package com.lms.support.controller;

import com.lms.common.enums.SupportTicketStatus;
import com.lms.support.dto.SupportDto;
import com.lms.support.service.SupportTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Ticket hỗ trợ — xem docblock {@code SupportTicketService}. */
@RestController
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    /** PUBLIC (SecurityConfig.PUBLIC_ENDPOINTS) — khách chưa đăng nhập cũng gửi được. */
    @PostMapping(value = "/api/v1/support/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupportDto.CreateRes> create(
            HttpServletRequest http,
            @Valid @RequestPart("request") SupportDto.CreateReq req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(supportTicketService.create(clientIp(http), req, files));
    }

    @GetMapping("/api/v1/admin/support/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportDto.AdminRes>> list(@RequestParam(required = false) SupportTicketStatus status) {
        return ResponseEntity.ok(supportTicketService.listForAdmin(status));
    }

    @GetMapping("/api/v1/admin/support/tickets/open-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportDto.OpenCountRes> openCount() {
        return ResponseEntity.ok(supportTicketService.countOpen());
    }

    @PatchMapping("/api/v1/admin/support/tickets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportDto.AdminRes> update(
            Principal principal, @PathVariable Long id, @Valid @RequestBody SupportDto.UpdateReq req) {
        return ResponseEntity.ok(supportTicketService.update(principal.getName(), id, req));
    }

    /** Sau reverse proxy (Caddy) — lấy IP đầu của X-Forwarded-For, cùng cách {@code AuthService.extractClientIp}. */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
