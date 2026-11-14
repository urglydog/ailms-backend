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
    private final com.lms.payment.service.RefundAppealService appealService;
    private final com.lms.finance.payos.PayosPayoutService payosPayoutService;

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

    /** Danh sách ngân hàng cho form khai báo tài khoản nhận hoàn tiền (học viên chọn từ danh sách, không gõ tự do). */
    @GetMapping("/banks")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<List<com.lms.finance.dto.PayoutDto.BankRes>> banks() {
        return ResponseEntity.ok(com.lms.finance.BankDirectory.all().stream()
                .map(b -> new com.lms.finance.dto.PayoutDto.BankRes(b.code(), b.name())).toList());
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

    /** Admin đã chuyển khoản tiền hoàn cho học viên và nhập mã giao dịch (11/11/2026). */
    @PostMapping("/admin/{id}/mark-transferred")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeDto.AdminRes> markTransferred(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.MarkTransferredReq req) {
        payosPayoutService.assertNoInFlightOrder(com.lms.common.enums.PayosOrderKind.DISPUTE_REFUND, id);
        return ResponseEntity.ok(disputeService.markTransferred(principal.getName(), id, req));
    }

    /** Admin quyết định khiếu nại của giảng viên với quyết định "giảng viên chịu toàn bộ" (14/11/2026). */
    @PostMapping("/admin/{id}/appeal/decide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeDto.AdminRes> decideAppeal(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.AppealDecideReq req) {
        appealService.decide(principal.getName(), id, req.decision(), req.note());
        return ResponseEntity.ok(disputeService.getAdmin(id));
    }

    /** User báo "chưa nhận được tiền" sau khi admin đã đánh dấu hoàn tiền (26/10/2026). */
    @PostMapping("/mine/{id}/reopen")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<DisputeDto.Res> reopen(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.ReopenReq req) {
        return ResponseEntity.ok(disputeService.reopen(principal.getName(), id, req));
    }
}
