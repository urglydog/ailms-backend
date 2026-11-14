package com.lms.finance.payos;

import com.lms.common.enums.PayosOrderKind;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin chi tự động qua PayOS cho rút tiền giảng viên / hoàn tiền học viên (14/11/2026). Chỉ ADMIN; số tiền/tài khoản lấy từ DB, không từ request. */
@RestController
@RequestMapping("/api/v1/admin/payos-payout")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayosPayoutController {

    private final PayosPayoutService service;

    @GetMapping("/status")
    public ResponseEntity<PayosDto.StatusRes> status() {
        return ResponseEntity.ok(service.status());
    }

    @GetMapping("/orders")
    public ResponseEntity<List<PayosDto.OrderRes>> orders(@RequestParam PayosOrderKind kind, @RequestParam Long entityId) {
        return ResponseEntity.ok(service.orders(kind, entityId));
    }

    @PostMapping("/instructor-payouts/{payoutId}/transfer")
    public ResponseEntity<PayosDto.OrderRes> transferPayout(Principal principal, @PathVariable Long payoutId) {
        return ResponseEntity.ok(service.transferInstructorPayout(principal.getName(), payoutId));
    }

    @PostMapping("/disputes/{disputeId}/refund-transfer")
    public ResponseEntity<PayosDto.OrderRes> transferRefund(Principal principal, @PathVariable Long disputeId) {
        return ResponseEntity.ok(service.transferRefund(principal.getName(), disputeId));
    }
}
