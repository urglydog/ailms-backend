package com.lms.finance.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.PayoutStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.NameNormalizer;
import com.lms.finance.PayoutSchedule;
import com.lms.finance.dto.PayoutDto;
import com.lms.finance.entity.InstructorPayout;
import com.lms.finance.entity.InstructorPayoutAccount;
import com.lms.finance.repository.InstructorPayoutAccountRepository;
import com.lms.finance.repository.InstructorPayoutRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rút tiền của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C). Máy trạng thái DUY NHẤT cho mọi đường
 * đi của tiền ra khỏi nền tảng:
 * <pre>
 *   PENDING_EMAIL --xác nhận email--> REQUESTED --admin duyệt--> APPROVED --admin chuyển khoản--> PAID
 *   PENDING_EMAIL/REQUESTED --giảng viên hủy / quá hạn email--> CANCELLED
 *   REQUESTED/APPROVED --admin từ chối--> REJECTED ; APPROVED --chuyển khoản lỗi--> FAILED
 * </pre>
 * Giữ chỗ số tiền bằng dòng âm trong sổ cái NGAY khi yêu cầu (chống rút trùng); CANCELLED/REJECTED/FAILED trả lại bằng
 * dòng dương. Mọi chuyển trạng thái chạy trong 1 transaction có khóa dòng, kèm nhật ký kiểm toán.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    static final int EMAIL_CONFIRM_MINUTES = 30;
    private static final Set<PayoutStatus> OPEN_STATUSES = Set.of(PayoutStatus.PENDING_EMAIL, PayoutStatus.REQUESTED, PayoutStatus.APPROVED);
    private static final DateTimeFormatter CODE_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final InstructorPayoutRepository payoutRepository;
    private final InstructorPayoutAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SystemConfigService systemConfigService;
    private final FinanceConfirmationService confirmations;
    private final FinanceAuditService auditService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final PayoutCrypto crypto;
    private final FinanceRateLimit rateLimit;
    private final PayoutAccountService accountService;
    private final SecureRandom random = new SecureRandom();

    // ── Giảng viên ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayoutDto.SummaryRes summary(String email) {
        User user = requireUser(email);
        LedgerService.Balance b = ledgerService.getBalance(user.getId());
        boolean hasOpen = payoutRepository.existsByInstructor_IdAndStatusIn(user.getId(), OPEN_STATUSES);
        return new PayoutDto.SummaryRes(
                b.available(), b.pending(), b.frozen(), b.withdrawable(),
                payoutRepository.sumPaid(user.getId()),
                BigDecimal.valueOf(minAmount()),
                PayoutSchedule.nextPayoutDate(LocalDate.now(), payoutDays()),
                accountService.getCurrent(email),
                crypto.isConfigured(),
                hasOpen);
    }

    @Transactional(readOnly = true)
    public List<PayoutDto.PayoutRes> listMine(String email) {
        User user = requireUser(email);
        return payoutRepository.findByInstructor_IdOrderByCreatedAtDesc(user.getId()).stream().map(PayoutService::toRes).toList();
    }

    /**
     * Tạo yêu cầu rút. {@code idempotencyKey} (header do FE sinh 1 lần cho mỗi lần bấm) → bấm đúp trả về đúng yêu cầu cũ.
     * Khóa dòng giảng viên làm mutex nên 2 yêu cầu song song không thể cùng vượt số dư.
     */
    @Transactional
    public PayoutDto.PayoutRes request(String email, BigDecimal amountInput, String idempotencyKey) {
        User user = requireUser(email);
        rateLimit.check("payout-request", user.getId(), 5);
        crypto.requireConfigured();
        user = userRepository.findByIdForUpdate(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User", email));

        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) {
            throw new InvalidRequestException("Idempotency-Key quá dài");
        }
        if (key != null) {
            Optional<InstructorPayout> existing = payoutRepository.findByInstructor_IdAndIdempotencyKey(user.getId(), key);
            if (existing.isPresent()) {
                return toRes(existing.get());
            }
        }
        if (user.getRole() != Role.INSTRUCTOR) {
            throw new AccessDeniedDomainException("Chỉ giảng viên mới rút tiền được");
        }
        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getIsSpamFlagged())) {
            throw new BusinessRuleViolationException("Tài khoản của bạn đang bị hạn chế nên chưa thể rút tiền. Vui lòng liên hệ hỗ trợ.");
        }
        InstructorPayoutAccount account = accountRepository.findFirstByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleViolationException("Bạn chưa liên kết tài khoản nhận tiền"));
        if (account.getUsableFrom() != null && account.getUsableFrom().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("Tài khoản nhận tiền mới đổi sẽ dùng được từ "
                    + account.getUsableFrom().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
        }
        if (payoutRepository.existsByInstructor_IdAndStatusIn(user.getId(), OPEN_STATUSES)) {
            throw new BusinessRuleViolationException("Bạn đang có một yêu cầu rút tiền chưa hoàn tất");
        }

        BigDecimal amount = amountInput.stripTrailingZeros();
        if (amount.scale() > 0) {
            throw new InvalidRequestException("Số tiền phải là số nguyên (VND)");
        }
        amount = amount.setScale(0);
        long min = minAmount();
        if (amount.compareTo(BigDecimal.valueOf(min)) < 0) {
            throw new BusinessRuleViolationException("Số tiền rút tối thiểu là " + formatVnd(BigDecimal.valueOf(min)));
        }
        BigDecimal withdrawable = ledgerService.getBalance(user.getId()).withdrawable();
        if (amount.compareTo(withdrawable) > 0) {
            throw new BusinessRuleViolationException("Số tiền vượt quá số dư có thể rút (" + formatVnd(withdrawable) + ")");
        }

        InstructorPayout payout = new InstructorPayout();
        payout.setCode(newCode());
        payout.setInstructor(user);
        payout.setAmount(amount);
        payout.setStatus(PayoutStatus.PENDING_EMAIL);
        payout.setAccount(account);
        payout.setBankCode(account.getBankCode());
        payout.setBankName(account.getBankName());
        payout.setAccountNumberEnc(account.getAccountNumberEnc());
        payout.setAccountLast4(account.getAccountLast4());
        payout.setHolderName(account.getHolderName());
        payout.setIdempotencyKey(key);
        InstructorPayout saved = payoutRepository.save(payout);

        ledgerService.recordPayoutReserve(user, amount, saved.getId(), saved.getCode());
        audit(email, "PAYOUT_REQUESTED", saved, null, snapshot(saved));

        String token = confirmations.issue(FinanceConfirmationService.TYPE_PAYOUT, saved.getId(), user.getId());
        // Email xác nhận BẮT BUỘC gửi được; lỗi thì ném → rollback cả yêu cầu lẫn khoản giữ chỗ.
        emailService.sendPayoutRequestConfirmEmail(user.getEmail(), saved.getCode(), amount, token);
        return toRes(saved);
    }

    /** Bấm link xác nhận trong email → REQUESTED, xếp vào đợt chi kế tiếp. */
    @Transactional
    public PayoutDto.PayoutRes confirmByEmail(Long userId, Long payoutId) {
        InstructorPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        if (!payout.getInstructor().getId().equals(userId)) {
            throw new AccessDeniedDomainException("Liên kết xác nhận này không dành cho tài khoản của bạn");
        }
        if (payout.getStatus() != PayoutStatus.PENDING_EMAIL) {
            throw new BusinessRuleViolationException("Yêu cầu rút này đã được xác nhận hoặc đã bị hủy");
        }
        payout.setStatus(PayoutStatus.REQUESTED);
        payout.setEmailConfirmedAt(LocalDateTime.now());
        payout.setScheduledFor(PayoutSchedule.nextPayoutDate(LocalDate.now(), payoutDays()));
        InstructorPayout saved = payoutRepository.save(payout);

        audit(payout.getInstructor().getEmail(), "PAYOUT_CONFIRMED", saved, Map.of("status", "PENDING_EMAIL"), snapshot(saved));
        emailService.sendPayoutRequestedEmail(payout.getInstructor().getEmail(), saved.getCode(), saved.getAmount(), saved.getScheduledFor());
        userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), "PAYOUT_REQUESTED",
                "Yêu cầu rút tiền mới", saved.getInstructor().getFullName() + " yêu cầu rút " + formatVnd(saved.getAmount())
                        + " (" + saved.getCode() + ").", "/admin/transactions/payouts"));
        return toRes(saved);
    }

    /**
     * Xác nhận bằng token trong email (tài khoản nhận tiền hoặc yêu cầu rút). Token bị tiêu thụ ngay cả khi người dùng
     * không đúng chủ → người khác nhặt được link cũng không dùng lại được; thông báo lỗi luôn chung chung (không lộ gì).
     */
    @Transactional
    public PayoutDto.ConfirmRes confirmToken(String email, String token) {
        User user = requireUser(email);
        rateLimit.check("finance-confirm", user.getId(), 10);
        FinanceConfirmationService.Confirmation c = confirmations.consume(token)
                .filter(x -> x.userId().equals(user.getId()))
                .orElseThrow(() -> new InvalidRequestException("Liên kết xác nhận không hợp lệ hoặc đã hết hạn"));
        if (FinanceConfirmationService.TYPE_ACCOUNT.equals(c.type())) {
            accountService.confirm(user.getId(), c.entityId());
        } else if (FinanceConfirmationService.TYPE_PAYOUT.equals(c.type())) {
            confirmByEmail(user.getId(), c.entityId());
        } else {
            throw new InvalidRequestException("Liên kết xác nhận không hợp lệ hoặc đã hết hạn");
        }
        return new PayoutDto.ConfirmRes(c.type(), c.entityId());
    }

    /** Giảng viên tự hủy khi chưa được admin duyệt. */
    @Transactional
    public PayoutDto.PayoutRes cancel(String email, Long payoutId) {
        InstructorPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        if (!payout.getInstructor().getEmail().equals(email)) {
            throw new AccessDeniedDomainException("Bạn chỉ hủy được yêu cầu rút của chính mình");
        }
        requireStatus(payout, Set.of(PayoutStatus.PENDING_EMAIL, PayoutStatus.REQUESTED), "Chỉ hủy được yêu cầu chưa được duyệt");
        return toRes(release(payout, PayoutStatus.CANCELLED, email, "PAYOUT_CANCELLED", null));
    }

    // ── Admin ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PayoutDto.AdminPayoutRes> listForAdmin(PayoutStatus status) {
        List<InstructorPayout> rows = status == null
                ? payoutRepository.findAllByOrderByCreatedAtDesc()
                : payoutRepository.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(PayoutService::toAdminRes).toList();
    }

    @Transactional
    public PayoutDto.AdminPayoutRes approve(String adminEmail, Long payoutId) {
        InstructorPayout payout = lock(payoutId);
        requireStatus(payout, Set.of(PayoutStatus.REQUESTED), "Chỉ duyệt được yêu cầu đã được giảng viên xác nhận qua email");
        User admin = requireUser(adminEmail);
        payout.setStatus(PayoutStatus.APPROVED);
        payout.setApprovedBy(admin);
        payout.setApprovedAt(LocalDateTime.now());
        InstructorPayout saved = payoutRepository.save(payout);
        audit(adminEmail, "PAYOUT_APPROVED", saved, Map.of("status", "REQUESTED"), snapshot(saved));
        return toAdminRes(saved);
    }

    /** Admin đã chuyển khoản tay: bắt buộc mã giao dịch. */
    @Transactional
    public PayoutDto.AdminPayoutRes markPaid(String adminEmail, Long payoutId, String transferRef, String proofUrl) {
        InstructorPayout payout = lock(payoutId);
        requireStatus(payout, Set.of(PayoutStatus.APPROVED), "Chỉ ghi nhận đã chi cho yêu cầu đã duyệt");
        if (transferRef == null || transferRef.isBlank()) {
            throw new InvalidRequestException("Cần nhập mã giao dịch chuyển khoản");
        }
        User admin = requireUser(adminEmail);
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidBy(admin);
        payout.setPaidAt(LocalDateTime.now());
        payout.setTransferRef(transferRef.trim());
        payout.setProofUrl(proofUrl == null || proofUrl.isBlank() ? null : proofUrl.trim());
        InstructorPayout saved = payoutRepository.save(payout);

        audit(adminEmail, "PAYOUT_PAID", saved, Map.of("status", "APPROVED"), snapshot(saved));
        emailService.sendPayoutPaidEmail(saved.getInstructor().getEmail(), saved.getCode(), saved.getAmount(), saved.getTransferRef());
        notificationService.notify(saved.getInstructor().getId(), "PAYOUT_PAID", "Đã chuyển tiền cho bạn",
                "Yêu cầu rút " + saved.getCode() + " (" + formatVnd(saved.getAmount()) + ") đã được chuyển khoản.", "/instructor/earnings");
        return toAdminRes(saved);
    }

    @Transactional
    public PayoutDto.AdminPayoutRes reject(String adminEmail, Long payoutId, String reason) {
        InstructorPayout payout = lock(payoutId);
        requireStatus(payout, Set.of(PayoutStatus.REQUESTED, PayoutStatus.APPROVED), "Chỉ từ chối được yêu cầu đang chờ xử lý");
        requireReason(reason);
        return toAdminRes(release(payout, PayoutStatus.REJECTED, adminEmail, "PAYOUT_REJECTED", reason.trim()));
    }

    /** Chuyển khoản thất bại (sai số tài khoản, ngân hàng từ chối...). */
    @Transactional
    public PayoutDto.AdminPayoutRes markFailed(String adminEmail, Long payoutId, String reason) {
        InstructorPayout payout = lock(payoutId);
        requireStatus(payout, Set.of(PayoutStatus.APPROVED), "Chỉ đánh dấu thất bại cho yêu cầu đã duyệt");
        requireReason(reason);
        return toAdminRes(release(payout, PayoutStatus.FAILED, adminEmail, "PAYOUT_FAILED", reason.trim()));
    }

    /** Admin xem số tài khoản ĐẦY ĐỦ của 1 yêu cầu — mỗi lần xem ghi nhật ký. */
    @Transactional
    public PayoutDto.AccountRevealRes revealAccount(String adminEmail, Long payoutId) {
        InstructorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        String number = accountService.decryptForAdmin(payout.getAccountNumberEnc());
        auditService.record(adminEmail, "PAYOUT_ACCOUNT_VIEWED", "payout", payout.getCode(), null,
                Map.of("last4", payout.getAccountLast4()));
        return new PayoutDto.AccountRevealRes(payout.getId(), payout.getBankName(), number, payout.getHolderName());
    }

    /** CSV các yêu cầu ĐÃ DUYỆT để chuyển khoản hàng loạt. Có số tài khoản đầy đủ nên ghi nhật ký (không ghi số). */
    @Transactional
    public String exportApprovedCsv(String adminEmail) {
        List<InstructorPayout> rows = payoutRepository.findByStatusOrderByCreatedAtDesc(PayoutStatus.APPROVED);
        StringBuilder sb = new StringBuilder("Mã yêu cầu,Giảng viên,Ngân hàng,Số tài khoản,Chủ tài khoản,Số tiền,Nội dung\n");
        for (InstructorPayout p : rows) {
            sb.append(csv(p.getCode())).append(',')
                    .append(csv(p.getInstructor().getFullName())).append(',')
                    .append(csv(p.getBankName())).append(',')
                    .append(csv(accountService.decryptForAdmin(p.getAccountNumberEnc()))).append(',')
                    .append(csv(p.getHolderName())).append(',')
                    .append(p.getAmount().toBigInteger()).append(',')
                    .append(csv("LINGUALEARN " + p.getCode())).append('\n');
        }
        auditService.record(adminEmail, "PAYOUT_EXPORT_CSV", "payout", null, null,
                Map.of("count", rows.size(), "codes", rows.stream().map(InstructorPayout::getCode).toList()));
        return sb.toString();
    }

    /** Job: yêu cầu quá hạn xác nhận email → hủy và trả lại khoản giữ chỗ. Trả số yêu cầu đã hủy. */
    @Transactional
    public int expireUnconfirmed(LocalDateTime now) {
        List<InstructorPayout> stale = payoutRepository.findByStatusAndCreatedAtBefore(
                PayoutStatus.PENDING_EMAIL, now.minusMinutes(EMAIL_CONFIRM_MINUTES));
        int count = 0;
        for (InstructorPayout p : stale) {
            InstructorPayout locked = payoutRepository.findByIdForUpdate(p.getId()).orElse(null);
            if (locked != null && locked.getStatus() == PayoutStatus.PENDING_EMAIL) {
                release(locked, PayoutStatus.CANCELLED, null, "PAYOUT_EXPIRED", "Quá hạn xác nhận email");
                count++;
            }
        }
        return count;
    }

    // ── nội bộ ──────────────────────────────────────────────────────────

    private InstructorPayout release(InstructorPayout payout, PayoutStatus target, String actorEmail, String action, String reason) {
        PayoutStatus before = payout.getStatus();
        payout.setStatus(target);
        if (reason != null) {
            payout.setRejectReason(reason);
        }
        InstructorPayout saved = payoutRepository.save(payout);
        ledgerService.recordPayoutRelease(saved.getInstructor(), saved.getAmount(), saved.getId(), saved.getCode());
        audit(actorEmail, action, saved, Map.of("status", before.name()), snapshot(saved));
        if (target == PayoutStatus.REJECTED || target == PayoutStatus.FAILED) {
            emailService.sendPayoutRejectedEmail(saved.getInstructor().getEmail(), saved.getCode(), saved.getAmount(),
                    reason, target == PayoutStatus.FAILED);
            notificationService.notify(saved.getInstructor().getId(), "PAYOUT_REJECTED",
                    target == PayoutStatus.FAILED ? "Chuyển tiền thất bại" : "Yêu cầu rút tiền bị từ chối",
                    "Yêu cầu " + saved.getCode() + ": " + reason + ". Số tiền đã được trả lại vào số dư của bạn.", "/instructor/earnings");
        }
        return saved;
    }

    private InstructorPayout lock(Long payoutId) {
        return payoutRepository.findByIdForUpdate(payoutId).orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
    }

    private static void requireStatus(InstructorPayout payout, Set<PayoutStatus> allowed, String message) {
        if (!allowed.contains(payout.getStatus())) {
            throw new BusinessRuleViolationException(message + " (trạng thái hiện tại: " + payout.getStatus() + ")");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Cần nhập lý do");
        }
    }

    private void audit(String actorEmail, String action, InstructorPayout p, Object before, Object after) {
        auditService.record(actorEmail, action, "payout", p.getCode(), before, after);
    }

    private static Map<String, Object> snapshot(InstructorPayout p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", p.getStatus().name());
        m.put("amount", p.getAmount());
        m.put("bank", p.getBankCode());
        m.put("last4", p.getAccountLast4());
        return m;
    }

    private String newCode() {
        String month = LocalDate.now().format(CODE_MONTH);
        for (int i = 0; i < 8; i++) {
            String code = "PO-" + month + "-" + String.format("%06d", random.nextInt(1_000_000));
            if (!payoutRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được mã yêu cầu rút duy nhất");
    }

    private long minAmount() {
        return systemConfigService.getInt(FinanceConfigKeys.PAYOUT_MIN_AMOUNT, 100_000);
    }

    private String payoutDays() {
        return systemConfigService.getString(FinanceConfigKeys.PAYOUT_DAYS_OF_MONTH, "5,20");
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    static String formatVnd(BigDecimal amount) {
        return String.format("%,.0fđ", amount);
    }

    /** Chống CSV injection: ô bắt đầu bằng = + - @ bị Excel hiểu là công thức → thêm dấu nháy; bọc nháy kép, nhân đôi nháy kép trong ô. */
    static String csv(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static PayoutDto.PayoutRes toRes(InstructorPayout p) {
        return new PayoutDto.PayoutRes(p.getId(), p.getCode(), p.getAmount(), p.getStatus(), p.getBankName(),
                PayoutCrypto.mask(p.getAccountLast4()), p.getHolderName(), p.getScheduledFor(), p.getCreatedAt(),
                p.getPaidAt(), p.getTransferRef(), p.getRejectReason());
    }

    static PayoutDto.AdminPayoutRes toAdminRes(InstructorPayout p) {
        User u = p.getInstructor();
        return new PayoutDto.AdminPayoutRes(p.getId(), p.getCode(), p.getAmount(), p.getStatus(), u.getId(), u.getFullName(),
                u.getEmail(), p.getBankCode(), p.getBankName(), PayoutCrypto.mask(p.getAccountLast4()), p.getHolderName(),
                NameNormalizer.matches(p.getHolderName(), u.getFullName()), p.getScheduledFor(), p.getCreatedAt(),
                p.getApprovedAt(), p.getPaidAt(), p.getTransferRef(), p.getProofUrl(), p.getRejectReason());
    }
}
