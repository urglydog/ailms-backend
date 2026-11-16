package com.lms.finance.controller;

import com.lms.common.enums.PayoutStatus;
import com.lms.finance.dto.PayoutDto;
import com.lms.finance.service.PayoutService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin duyệt/chi/từ chối yêu cầu rút tiền của giảng viên (11/11/2026, Giai đoạn C). Chỉ ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private final PayoutService payoutService;

    @GetMapping
    public ResponseEntity<List<PayoutDto.AdminPayoutRes>> list(@RequestParam(required = false) PayoutStatus status) {
        return ResponseEntity.ok(payoutService.listForAdmin(status));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PayoutDto.AdminPayoutRes> approve(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(payoutService.approve(principal.getName(), id));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<PayoutDto.AdminPayoutRes> markPaid(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PayoutDto.MarkPaidReq req) {
        return ResponseEntity.ok(payoutService.markPaid(principal.getName(), id, req.transferRef(), req.proofUrl()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PayoutDto.AdminPayoutRes> reject(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PayoutDto.ReasonReq req) {
        return ResponseEntity.ok(payoutService.reject(principal.getName(), id, req.reason()));
    }

    @PostMapping("/{id}/mark-failed")
    public ResponseEntity<PayoutDto.AdminPayoutRes> markFailed(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PayoutDto.ReasonReq req) {
        return ResponseEntity.ok(payoutService.markFailed(principal.getName(), id, req.reason()));
    }

    /** Số tài khoản đầy đủ của 1 yêu cầu — mỗi lần gọi được ghi nhật ký kiểm toán. */
    @GetMapping("/{id}/account")
    public ResponseEntity<PayoutDto.AccountRevealRes> reveal(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(payoutService.revealAccount(principal.getName(), id));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(Principal principal) {
        byte[] body = payoutService.exportApprovedCsv(principal.getName()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payouts-approved.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
