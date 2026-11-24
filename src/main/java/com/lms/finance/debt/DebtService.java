package com.lms.finance.debt;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.DebtPaymentStatus;
import com.lms.common.enums.LedgerEntryType;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.entity.InstructorDebtPayment;
import com.lms.finance.entity.InstructorDebtState;
import com.lms.finance.repository.InstructorDebtPaymentRepository;
import com.lms.finance.repository.InstructorDebtStateRepository;
import com.lms.finance.repository.InstructorLedgerEntryRepository;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

/**
 * Công nợ giảng viên (14/11/2026). Nợ = số dư khả dụng ÂM trong sổ cái (hoàn tiền muộn, chế độ "giảng viên chịu toàn bộ"); doanh thu mới
 * tự bù trừ khi chín. Giảng viên còn có thể trả nợ chủ động (một phần hoặc hết) qua link/QR PayOS. Bậc thang theo số ngày nợ liên tục:
 * xem {@link DebtGuard}. Không khóa tài khoản và không đánh dấu spam; admin có thể xóa nợ có lý do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebtService {

    /** Dải orderCode dành riêng cho trả nợ: đơn mua khóa luôn &lt; 1e9 nên không bao giờ trùng. */
    static final long ORDER_CODE_BASE = 1_000_000_000_000L;
    private static final long DEFAULT_MIN_REPAY = 10_000L;

    private final LedgerService ledgerService;
    private final InstructorLedgerEntryRepository ledgerRepository;
    private final InstructorDebtStateRepository stateRepository;
    private final InstructorDebtPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FinanceAuditService auditService;
    private final SystemConfigService config;
    private final DebtGuard guard;
    private final PayOS payOS;
    private final PlatformTransactionManager txManager;

    @Value("${payment.payos.returnUrl:http://localhost:3000/payments/callback}")
    private String payosReturnUrl;

    // ── đọc ──────────────────────────────────────────────────────────────

    /** Số nợ hiện tại (VND, ≥ 0) = phần ÂM của số dư khả dụng. */
    public long debtOf(Long instructorId) {
        BigDecimal available = ledgerService.getBalance(instructorId).available();
        return available.signum() < 0 ? available.negate().longValue() : 0L;
    }

    @Transactional
    public DebtDto.Res status(String email) {
        User user = requireUser(email);
        long debt = debtOf(user.getId());
        LocalDateTime now = LocalDateTime.now();
        InstructorDebtState state = syncState(user.getId(), debt, now);

        int level = 0;
        long days = 0;
        boolean paused = false;
        if (state != null) {
            days = ChronoUnit.DAYS.between(state.getDebtSince(), now);
            int raw = guard.levelFor(state.getDebtSince(), now);
            level = guard.effectiveLevel(state, now);
            paused = level < raw;
        }
        List<DebtDto.ChargeRes> charges = ledgerRepository
                .findTop5ByInstructor_IdAndEntryTypeOrderByCreatedAtDescIdDesc(user.getId(), LedgerEntryType.REFUND_REVERSAL).stream()
                .map(e -> new DebtDto.ChargeRes(e.getNote(), e.getAmount().negate().longValue(), e.getCreatedAt()))
                .toList();
        List<DebtDto.RepaymentRes> pending = paymentRepository.findByInstructor_IdAndStatus(user.getId(), DebtPaymentStatus.PENDING).stream()
                .map(DebtService::toRes).toList();
        return new DebtDto.Res(debt, state == null ? null : state.getDebtSince(), days, level,
                guard.restrictDays(), guard.suspendDays(), paused, minRepay(), charges, pending);
    }

    @Transactional(readOnly = true)
    public DebtDto.RepaymentRes repayment(String email, Long id) {
        User user = requireUser(email);
        InstructorDebtPayment p = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("DebtPayment", id));
        if (!p.getInstructor().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DebtPayment", id);
        }
        return toRes(p);
    }

    @Transactional(readOnly = true)
    public List<DebtDto.AdminDebtRes> listDebts() {
        LocalDateTime now = LocalDateTime.now();
        List<DebtDto.AdminDebtRes> out = new ArrayList<>();
        for (InstructorDebtState s : stateRepository.findAll()) {
            User u = userRepository.findById(s.getInstructorId()).orElse(null);
            long debt = debtOf(s.getInstructorId());
            if (u == null || debt <= 0) {
                continue;
            }
            out.add(new DebtDto.AdminDebtRes(u.getId(), u.getFullName(), u.getEmail(), debt, s.getDebtSince(),
                    ChronoUnit.DAYS.between(s.getDebtSince(), now), guard.effectiveLevel(s, now)));
        }
        out.sort((a, b) -> Long.compare(b.daysInDebt(), a.daysInDebt()));
        return out;
    }

    // ── trả nợ qua PayOS ─────────────────────────────────────────────────

    /** Tạo link/QR trả nợ cho một phần hoặc toàn bộ nợ. Các link chờ cũ của người này bị hủy để không còn nhiều mã song song. */
    @Transactional
    public DebtDto.RepaymentRes createRepayment(String email, long amount) {
        User user = requireUser(email);
        long debt = debtOf(user.getId());
        if (debt <= 0) {
            throw new BusinessRuleViolationException("Bạn không có công nợ cần trả");
        }
        if (amount > debt) {
            throw new BusinessRuleViolationException("Số tiền vượt quá công nợ hiện tại (" + debt + "đ)");
        }
        if (amount < minRepay() && amount != debt) {
            throw new BusinessRuleViolationException("Mỗi lần trả tối thiểu " + minRepay() + "đ (hoặc trả hết phần còn lại)");
        }
        for (InstructorDebtPayment old : paymentRepository.findByInstructor_IdAndStatus(user.getId(), DebtPaymentStatus.PENDING)) {
            try {
                payOS.paymentRequests().cancel(old.getOrderCode(), "Thay bằng mã trả nợ mới");
                old.setStatus(DebtPaymentStatus.CANCELLED);
                paymentRepository.save(old);
            } catch (RuntimeException e) {
                // Link cũ có thể đã được trả/hết hạn: để đối soát nền quyết định, không hủy cưỡng bức.
                log.warn("Không hủy được link trả nợ cũ {}: {}", old.getOrderCode(), e.getMessage());
            }
        }

        InstructorDebtPayment p = new InstructorDebtPayment();
        p.setInstructor(user);
        p.setAmount(amount);
        p.setOrderCode(newOrderCode());
        p.setStatus(DebtPaymentStatus.PENDING);
        p = paymentRepository.save(p);

        String origin = originOf(payosReturnUrl);
        try {
            CreatePaymentLinkResponse link = payOS.paymentRequests().create(CreatePaymentLinkRequest.builder()
                    .orderCode(p.getOrderCode())
                    .amount(amount)
                    .description("Tra no LinguaLearn")
                    .returnUrl(origin + "/instructor/earnings?debt=success")
                    .cancelUrl(origin + "/instructor/earnings?debt=cancel")
                    .items(List.of(PaymentLinkItem.builder().name("Tra no cong no giang vien").quantity(1).price(amount).build()))
                    .build());
            p.setCheckoutUrl(link.getCheckoutUrl());
            p.setQrCode(link.getQrCode());
            p.setPaymentLinkId(link.getPaymentLinkId());
            p = paymentRepository.save(p);
        } catch (RuntimeException e) {
            log.error("PayOS: không tạo được link trả nợ", e);
            throw new BusinessRuleViolationException("Lỗi khởi tạo thanh toán PayOS, vui lòng thử lại sau");
        }
        auditService.record(email, "DEBT_REPAYMENT_CREATED", "instructor_debt_payment", String.valueOf(p.getId()), null,
                Map.of("amount", amount, "orderCode", p.getOrderCode()));
        return toRes(p);
    }

    /**
     * Webhook PayOS cho một orderCode. Trả {@code true} nếu orderCode thuộc về trả nợ (đã xử lý xong, controller không chuyển cho luồng
     * đơn mua nữa), {@code false} nếu không phải. Idempotent: webhook gửi lại không cộng sổ cái lần 2.
     */
    @Transactional
    public boolean handleWebhook(Long orderCode, Long paidAmount, String paymentLinkId, String code) {
        if (orderCode == null || orderCode < ORDER_CODE_BASE) {
            return false;
        }
        InstructorDebtPayment p = paymentRepository.findByOrderCode(orderCode).orElse(null);
        if (p == null) {
            return false;
        }
        if ("00".equals(code)) {
            settle(p, paidAmount, paymentLinkId);
        }
        return true;
    }

    /** Ghi nhận một lần trả nợ đã thành công: cộng sổ cái đúng 1 lần, báo giảng viên, cập nhật bậc nợ. */
    void settle(InstructorDebtPayment p, Long paidAmount, String paymentLinkId) {
        if (p.getStatus() == DebtPaymentStatus.PAID) {
            return;
        }
        if (paidAmount != null && paidAmount != p.getAmount().longValue()) {
            log.error("Trả nợ {}: PayOS báo {}đ khác số đã tạo {}đ — không ghi sổ, cần kiểm tra tay", p.getOrderCode(), paidAmount, p.getAmount());
            userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), "DEBT_AMOUNT_MISMATCH",
                    "Trả nợ lệch số tiền", "Mã " + p.getOrderCode() + ": PayOS báo " + paidAmount + "đ, hệ thống tạo " + p.getAmount()
                            + "đ. Cần kiểm tra thủ công.", "/admin/transactions/payouts"));
            return;
        }
        p.setStatus(DebtPaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now());
        if (paymentLinkId != null) {
            p.setPaymentLinkId(paymentLinkId);
        }
        try {
            paymentRepository.save(p);
            ledgerService.recordDebtRepayment(p.getInstructor(), BigDecimal.valueOf(p.getAmount()), p.getId());
        } catch (DataIntegrityViolationException e) {
            log.info("Trả nợ {} đã được ghi sổ trước đó", p.getOrderCode());
            return;
        }
        auditService.record(null, "DEBT_REPAYMENT_PAID", "instructor_debt_payment", String.valueOf(p.getId()), null,
                Map.of("amount", p.getAmount(), "orderCode", p.getOrderCode()));
        long remaining = debtOf(p.getInstructor().getId());
        syncState(p.getInstructor().getId(), remaining, LocalDateTime.now());
        notificationService.notify(p.getInstructor().getId(), "DEBT_REPAID", "Đã ghi nhận trả nợ",
                "Bạn đã trả " + p.getAmount() + "đ. " + (remaining > 0 ? "Công nợ còn lại: " + remaining + "đ." : "Bạn đã hết công nợ."),
                "/instructor/earnings");
    }

    /** Đối soát các link trả nợ còn chờ (phòng webhook không tới): hỏi PayOS trạng thái từng link. */
    @SchedulerLock(name = "DebtService.reconcilePending", lockAtMostFor = "PT10M", lockAtLeastFor = "PT4M30S")
    @Scheduled(fixedDelay = 300_000, initialDelay = 150_000)
    public void reconcilePending() {
        List<InstructorDebtPayment> open;
        try {
            open = paymentRepository.findByStatusIn(Set.of(DebtPaymentStatus.PENDING));
        } catch (RuntimeException e) {
            log.warn("Đối soát trả nợ: không đọc được danh sách: {}", e.getMessage());
            return;
        }
        for (InstructorDebtPayment p : open) {
            try {
                PaymentLinkStatus st = payOS.paymentRequests().get(p.getOrderCode()).getStatus();
                if (st == PaymentLinkStatus.PAID) {
                    settleInTx(p.getId());
                } else if (st == PaymentLinkStatus.CANCELLED || st == PaymentLinkStatus.EXPIRED || st == PaymentLinkStatus.FAILED) {
                    markDead(p.getId(), st == PaymentLinkStatus.EXPIRED ? DebtPaymentStatus.EXPIRED : DebtPaymentStatus.CANCELLED);
                }
            } catch (RuntimeException e) {
                log.warn("Đối soát trả nợ {} lỗi: {}", p.getOrderCode(), e.getMessage());
            }
        }
    }

    // Gọi nội bộ từ việc nền nên @Transactional sẽ không có tác dụng (không qua proxy) → dùng TransactionTemplate để đọc lazy + ghi trong 1 giao dịch.
    private void settleInTx(Long id) {
        new TransactionTemplate(txManager).executeWithoutResult(
                s -> paymentRepository.findById(id).ifPresent(p -> settle(p, null, null)));
    }

    private void markDead(Long id, DebtPaymentStatus status) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> paymentRepository.findById(id).ifPresent(p -> {
            if (p.getStatus() == DebtPaymentStatus.PENDING) {
                p.setStatus(status);
                paymentRepository.save(p);
            }
        }));
    }

    // ── theo dõi bậc nợ ──────────────────────────────────────────────────

    /** Mỗi đêm: dựng/xóa trạng thái nợ theo sổ cái thật và báo giảng viên khi lên bậc. */
    @Scheduled(cron = "0 20 3 * * *")
    @SchedulerLock(name = "DebtService.refreshAllStates", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void refreshAllStates() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> debtors = new java.util.HashSet<>();
        for (Object[] row : ledgerRepository.findDebtors(now.plusSeconds(2))) {
            Long id = ((Number) row[0]).longValue();
            debtors.add(id);
            syncState(id, ((BigDecimal) row[1]).negate().longValue(), now);
        }
        for (InstructorDebtState s : stateRepository.findAll()) {
            if (!debtors.contains(s.getInstructorId())) {
                stateRepository.delete(s);
            }
        }
    }

    /**
     * Đồng bộ trạng thái nợ của 1 giảng viên với số nợ thật: có nợ mà chưa có dòng → tạo (và báo); hết nợ → xóa dòng; lên bậc → báo.
     * Trả về dòng trạng thái hiện hành (null nếu không nợ).
     */
    InstructorDebtState syncState(Long instructorId, long debt, LocalDateTime now) {
        InstructorDebtState state = stateRepository.findById(instructorId).orElse(null);
        if (debt <= 0) {
            if (state != null) {
                stateRepository.delete(state);
            }
            return null;
        }
        if (state == null) {
            state = new InstructorDebtState();
            state.setInstructorId(instructorId);
            state.setDebtSince(now);
            state.setLastLevel(1);
            state = stateRepository.save(state);
            notificationService.notify(instructorId, "DEBT_CREATED", "Bạn có công nợ",
                    "Số dư của bạn đang âm " + debt + "đ (do hoàn tiền). Doanh thu mới sẽ tự bù trừ; bạn cũng có thể trả nợ chủ động ở trang Thu nhập. "
                            + "Sau " + guard.restrictDays() + " ngày chưa trả sẽ bị hạn chế xuất bản khóa mới và đăng ký quảng bá.",
                    "/instructor/earnings");
            return state;
        }
        int level = guard.levelFor(state.getDebtSince(), now);
        if (level > state.getLastLevel()) {
            state.setLastLevel(level);
            stateRepository.save(state);
            String msg = level == 2
                    ? "Công nợ " + debt + "đ đã quá " + guard.restrictDays() + " ngày: bạn tạm thời không xuất bản được khóa mới và không đăng ký quảng bá được."
                    : "Công nợ " + debt + "đ đã quá " + guard.suspendDays() + " ngày: các khóa của bạn tạm ngừng nhận đăng ký mới (học viên đã mua vẫn học bình thường).";
            notificationService.notify(instructorId, "DEBT_LEVEL_UP", "Công nợ quá hạn", msg + " Hãy trả nợ ở trang Thu nhập để gỡ hạn chế.",
                    "/instructor/earnings");
        }
        return state;
    }

    /** Đồng bộ lại trạng thái nợ của 1 giảng viên sau khi sổ cái đổi (vd. khiếu nại thành công được hoàn lại tiền phạt). */
    @Transactional
    public void refreshState(Long instructorId) {
        syncState(instructorId, debtOf(instructorId), LocalDateTime.now());
    }

    // ── admin ────────────────────────────────────────────────────────────

    /** Xóa nợ (nền tảng chịu) — bắt buộc lý do; ghi nhật ký kiểm toán và báo giảng viên. */
    @Transactional
    public void writeOff(String adminEmail, Long instructorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException("Cần nhập lý do xóa nợ");
        }
        User admin = requireUser(adminEmail);
        User instructor = userRepository.findById(instructorId).orElseThrow(() -> new ResourceNotFoundException("User", instructorId));
        long debt = debtOf(instructorId);
        if (debt <= 0) {
            throw new BusinessRuleViolationException("Giảng viên này không có công nợ");
        }
        ledgerService.recordAdjustment(instructor, BigDecimal.valueOf(debt), "Xóa nợ: " + reason.trim(), admin.getId());
        syncState(instructorId, 0, LocalDateTime.now());
        auditService.record(adminEmail, "DEBT_WRITTEN_OFF", "user", String.valueOf(instructorId), Map.of("debt", debt), Map.of("reason", reason.trim()));
        notificationService.notify(instructorId, "DEBT_WRITTEN_OFF", "Công nợ đã được xóa",
                "Quản trị viên đã xóa khoản nợ " + debt + "đ của bạn. Lý do: " + reason.trim(), "/instructor/earnings");
    }

    // ── tiện ích ─────────────────────────────────────────────────────────

    private long minRepay() {
        return config.getInt(FinanceConfigKeys.DEBT_MIN_REPAY, (int) DEFAULT_MIN_REPAY);
    }

    private long newOrderCode() {
        for (int i = 0; i < 5; i++) {
            long code = ORDER_CODE_BASE + (System.currentTimeMillis() % 1_000_000_000L) * 10 + ThreadLocalRandom.current().nextInt(10);
            if (!paymentRepository.existsByOrderCode(code)) {
                return code;
            }
        }
        throw new BusinessRuleViolationException("Không tạo được mã thanh toán, vui lòng thử lại");
    }

    /** "https://host[:port]" từ URL đầy đủ (return URL của luồng mua khóa) — mã trả nợ quay về đúng trang Thu nhập. */
    static String originOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private static DebtDto.RepaymentRes toRes(InstructorDebtPayment p) {
        return new DebtDto.RepaymentRes(p.getId(), p.getAmount(), p.getStatus(), p.getCheckoutUrl(), p.getQrCode(), p.getCreatedAt(), p.getPaidAt());
    }
}
