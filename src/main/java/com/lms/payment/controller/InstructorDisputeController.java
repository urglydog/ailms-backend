package com.lms.payment.controller;

import com.lms.common.enums.DisputeStatus;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.service.InstructorDisputeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Giảng viên xem/phản hồi khiếu nại hoàn tiền thuộc khóa của mình — xem {@code InstructorDisputeService}. */
@RestController
@RequestMapping("/api/v1/instructor/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorDisputeController {

    private final InstructorDisputeService instructorDisputeService;
    private final com.lms.payment.service.RefundAppealService appealService;

    @GetMapping
    public ResponseEntity<List<DisputeDto.InstructorRes>> list(
            Principal principal,
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(instructorDisputeService.list(principal.getName(), status, courseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeDto.InstructorRes> get(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(instructorDisputeService.get(principal.getName(), id));
    }

    /** Giảng viên khiếu nại quyết định "giảng viên chịu toàn bộ" (một lần, trong hạn). */
    @PostMapping("/{id}/appeal")
    public ResponseEntity<DisputeDto.InstructorRes> appeal(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.AppealReq req) {
        appealService.appeal(principal.getName(), id, req.reason());
        return ResponseEntity.ok(instructorDisputeService.get(principal.getName(), id));
    }

    /** multipart: part "request" (JSON) + part "files" optional (0–5 ảnh/video minh chứng). */
    @PostMapping(value = "/{id}/respond", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DisputeDto.InstructorRes> respond(
            Principal principal,
            @PathVariable Long id,
            @Valid @RequestPart("request") DisputeDto.InstructorRespondReq req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(instructorDisputeService.respond(principal.getName(), id, req, files));
    }
}
