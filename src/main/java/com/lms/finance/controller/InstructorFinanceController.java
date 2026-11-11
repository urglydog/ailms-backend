package com.lms.finance.controller;

import com.lms.finance.dto.PayoutDto;
import com.lms.finance.entity.InstructorLedgerEntry;
import com.lms.finance.service.LedgerService;
import com.lms.finance.service.PayoutAccountService;
import com.lms.finance.service.PayoutService;
import com.lms.auth.repository.UserRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thu nhập, tài khoản nhận tiền và rút tiền của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C).
 * Mọi API đều cần đăng nhập với vai trò INSTRUCTOR; không có API nào trả số tài khoản đầy đủ.
 */
@RestController
@RequestMapping("/api/v1/instructor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
public class InstructorFinanceController {

    private final PayoutService payoutService;
    private final PayoutAccountService accountService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;

    @GetMapping("/finance/summary")
    public ResponseEntity<PayoutDto.SummaryRes> summary(Principal principal) {
        return ResponseEntity.ok(payoutService.summary(principal.getName()));
    }

    /** Sao kê sổ cái (mới nhất trước), tối đa 100 dòng/trang. */
    @GetMapping("/finance/ledger")
    public ResponseEntity<List<PayoutDto.LedgerEntryRes>> ledger(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Long userId = userRepository.findByEmail(principal.getName()).orElseThrow().getId();
        LocalDateTime now = LocalDateTime.now();
        List<PayoutDto.LedgerEntryRes> rows = ledgerService.getStatement(userId, page, size).getContent().stream()
                .map((InstructorLedgerEntry e) -> new PayoutDto.LedgerEntryRes(e.getId(), e.getEntryType(), e.getAmount(),
                        e.getAvailableAt(), !e.getAvailableAt().isAfter(now), e.getNote(), e.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/payout-banks")
    public ResponseEntity<List<PayoutDto.BankRes>> banks() {
        return ResponseEntity.ok(accountService.banks());
    }

    @GetMapping("/payout-account")
    public ResponseEntity<PayoutDto.AccountRes> currentAccount(Principal principal) {
        PayoutDto.AccountRes account = accountService.getCurrent(principal.getName());
        return account == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(account);
    }

    @PostMapping("/payout-account")
    public ResponseEntity<PayoutDto.AccountRes> linkAccount(Principal principal, @Valid @RequestBody PayoutDto.LinkAccountReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.link(principal.getName(), req));
    }

    /** Xác nhận bằng token trong email (tài khoản nhận tiền hoặc yêu cầu rút). Token chỉ dùng được với đúng người đã được gửi. */
    @PostMapping("/finance/confirm")
    public ResponseEntity<PayoutDto.ConfirmRes> confirm(Principal principal, @Valid @RequestBody PayoutDto.ConfirmReq req) {
        return ResponseEntity.ok(payoutService.confirmToken(principal.getName(), req.token()));
    }

    @GetMapping("/payouts")
    public ResponseEntity<List<PayoutDto.PayoutRes>> myPayouts(Principal principal) {
        return ResponseEntity.ok(payoutService.listMine(principal.getName()));
    }

    /** {@code Idempotency-Key}: FE sinh 1 UUID cho mỗi lần bấm "Yêu cầu rút" — bấm đúp không tạo 2 yêu cầu. */
    @PostMapping("/payouts")
    public ResponseEntity<PayoutDto.PayoutRes> requestPayout(
            Principal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayoutDto.RequestPayoutReq req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payoutService.request(principal.getName(), req.amount(), idempotencyKey));
    }

    @PostMapping("/payouts/{id}/cancel")
    public ResponseEntity<PayoutDto.PayoutRes> cancel(Principal principal, @PathVariable Long id) {
        return ResponseEntity.ok(payoutService.cancel(principal.getName(), id));
    }
}
