package com.lms.finance.debt;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Công nợ của giảng viên: xem, trả (một phần được) qua PayOS; admin xem danh sách và xóa nợ (14/11/2026). */
@RestController
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @GetMapping("/api/v1/instructor/finance/debt")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DebtDto.Res> myDebt(Principal principal) {
        return ResponseEntity.ok(debtService.status(principal.getName()));
    }

    @PostMapping("/api/v1/instructor/finance/debt/repay")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DebtDto.RepaymentRes> repay(Principal principal, @Valid @RequestBody DebtDto.RepayReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debtService.createRepayment(principal.getName(), req.amount()));
    }

    @GetMapping("/api/v1/instructor/finance/debt/repayments/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<DebtDto.RepaymentRes> repayment(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(debtService.repayment(principal.getName(), id));
    }

    @GetMapping("/api/v1/admin/finance/debts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DebtDto.AdminDebtRes>> debts() {
        return ResponseEntity.ok(debtService.listDebts());
    }

    @PostMapping("/api/v1/admin/finance/debts/{instructorId}/write-off")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> writeOff(Principal principal, @PathVariable Long instructorId, @Valid @RequestBody DebtDto.WriteOffReq req) {
        debtService.writeOff(principal.getName(), instructorId, req.reason());
        return ResponseEntity.noContent().build();
    }
}
