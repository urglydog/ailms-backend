package com.lms.payment.controller;

import com.lms.common.enums.DisputeStatus;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.service.DisputeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Khiếu nại/hoàn tiền giao dịch — xem docblock {@code DisputeService}. */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /** multipart thay vì JSON thuần (23/10/2026) — cho đính kèm minh chứng ảnh/video ngay lúc
     * gửi khiếu nại, field "files" optional (0-5 file). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<DisputeDto.Res> file(
            Principal principal,
            @Valid @RequestPart("request") DisputeDto.FileReq req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(disputeService.fileDispute(principal.getName(), req, files));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<DisputeDto.Res>> getMine(Principal principal) {
        return ResponseEntity.ok(disputeService.getMine(principal.getName()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DisputeDto.AdminRes>> getAllForAdmin(@RequestParam(required = false) DisputeStatus status) {
        return ResponseEntity.ok(disputeService.getAllForAdmin(status));
    }

    @PostMapping("/admin/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeDto.AdminRes> resolve(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.ResolveReq req) {
        return ResponseEntity.ok(disputeService.resolve(principal.getName(), id, req));
    }
}
