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
    public ResponseEntity<List<DisputeDto.AdminRes>> getAllForAdmin(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean needsAction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(disputeService.getAllForAdmin(status, q, needsAction, page, size));
    }

    /** Số khiếu nại cần admin xử lý — cho huy hiệu menu (thay cho việc tải toàn bộ danh sách rồi đếm ở trình duyệt). */
    @GetMapping("/admin/open-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Long>> openCount() {
        return ResponseEntity.ok(java.util.Map.of("open", disputeService.countNeedingAdminAction()));
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
        return ResponseEntity.ok(disputeService.markTransferred(principal.getName(), id, req));
    }

    /** Admin yêu cầu học viên cập nhật tài khoản nhận hoàn tiền (16/11/2026). */
    @PostMapping("/admin/{id}/request-account-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisputeDto.AdminRes> requestAccountUpdate(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.RequestAccountUpdateReq req) {
        return ResponseEntity.ok(disputeService.requestAccountUpdate(principal.getName(), id, req.reason()));
    }

    /** Học viên cập nhật tài khoản nhận hoàn tiền (16/11/2026); BE kiểm tra được phép hay không theo trạng thái khoản hoàn. */
    @PutMapping("/mine/{id}/refund-account")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<DisputeDto.Res> updateRefundAccount(
            Principal principal, @PathVariable Long id, @Valid @RequestBody DisputeDto.UpdateRefundAccountReq req) {
        return ResponseEntity.ok(disputeService.updateRefundAccount(principal.getName(), id, req));
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
