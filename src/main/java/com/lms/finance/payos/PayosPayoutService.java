package com.lms.finance.payos;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import com.lms.common.enums.PayoutStatus;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.BankDirectory;
import com.lms.finance.NameNormalizer;
import com.lms.finance.entity.InstructorPayout;
import com.lms.finance.entity.PayosPayoutOrder;
import com.lms.finance.payos.PayosPayoutGateway.TransferRequest;
import com.lms.finance.payos.PayosPayoutGateway.TransferResult;
import com.lms.finance.repository.InstructorPayoutRepository;
import com.lms.finance.repository.PayosPayoutOrderRepository;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.PayoutAccountService;
import com.lms.finance.service.PayoutService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import com.lms.payment.service.DisputeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.payos.exception.APIException;

/**
 * Chi tự động qua PayOS (14/11/2026, lõi) cho 2 loại khoản: rút tiền của giảng viên và hoàn tiền cho học viên.
 *
 * <p>Nguyên tắc an toàn: (1) admin bấm cho từng lệnh, không tự chạy; (2) công tắc {@code PAYOS_PAYOUT_ENABLED} + trần
 * {@code PAYOS_PAYOUT_MAX_AMOUNT}; (3) số tiền/tài khoản lấy từ DB, không nhận từ request; (4) mỗi lệnh có {@code reference_id} duy nhất
 * làm khóa idempotency nên gửi lại không chi 2 lần; (5) kiểm số dư tài khoản chi trước khi gửi; (6) lỗi/không rõ kết quả KHÔNG tự đổi
 * trạng thái tiền (yêu cầu rút vẫn APPROVED, hoàn tiền vẫn chờ chuyển) mà báo admin; chỉ khi PayOS báo thành công mới ghi nhận đã chi
 * qua đúng đường ghi nhận thủ công sẵn có (nên email/thông báo/sổ cái chạy y như admin bấm tay); (7) không bao giờ ghi số tài khoản đầy đủ
 * vào DB/log/audit.
 *
 * <p>Lớp này cố ý KHÔNG {@code @Transactional}: lời gọi mạng sang PayOS không được giữ giao dịch DB; mỗi lần lưu là một giao dịch ngắn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayosPayoutService {

    static final String KEY_MAX_AMOUNT = "PAYOS_PAYOUT_MAX_AMOUNT";
    static final long DEFAULT_MAX_AMOUNT = 5_000_000L;
    private static final Set<PayosOrderStatus> IN_FLIGHT = Set.of(PayosOrderStatus.PENDING, PayosOrderStatus.SUBMITTED);
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("(\\d[\\d ]{4,22}\\d)");

    private final PayosPayoutGateway gateway;
    private final PayosPayoutOrderRepository orderRepository;
    private final InstructorPayoutRepository payoutRepository;
    private final PayoutService payoutService;
    private final PayoutAccountService accountService;
    private final PaymentRefundRepository refundRepository;
    private final PaymentDisputeRepository disputeRepository;
    private final DisputeService disputeService;
    private final SystemConfigService config;
    private final FinanceAuditService auditService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager txManager;

    /** Nguồn dữ liệu đã đọc xong từ DB (không còn phụ thuộc phiên Hibernate) cho 1 lệnh chi. */
    record Source(long amount, String bankBin, String accountNumber, String holderName, String description) {
        String last4() {
            return accountNumber.substring(accountNumber.length() - 4);
        }
    }

    // ── trạng thái / danh sách ───────────────────────────────────────────

    public PayosDto.StatusRes status() {
        boolean enabled = gateway.isEnabled();
        Long balance = null;
        String error = null;
        if (enabled) {
            try {
                balance = gateway.balance();
            } catch (RuntimeException e) {
                error = "Không đọc được số dư tài khoản chi PayOS: " + safeMessage(e);
            }
        }
        return new PayosDto.StatusRes(enabled, balance, error, maxAmount());
    }

    public List<PayosDto.OrderRes> orders(PayosOrderKind kind, Long entityId) {
        return orderRepository.findByKindAndEntityIdOrderByAttemptDesc(kind, entityId).stream().map(PayosPayoutService::toRes).toList();
    }

    /**
     * Chặn ghi nhận chuyển KHOẢN TAY khi đang có lệnh chi PayOS chưa chốt (đang xử lý hoặc chưa rõ kết quả): admin chuyển tay lúc đó có thể làm
     * tiền đi hai lần (một lần bằng tay, một lần qua PayOS). Gọi từ các endpoint ghi nhận thủ công; đường tự động không đi qua đây.
     */
    public void assertNoInFlightOrder(PayosOrderKind kind, Long entityId) {
        orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(kind, entityId).ifPresent(o -> {
            if (IN_FLIGHT.contains(o.getStatus()) || o.getStatus() == PayosOrderStatus.UNKNOWN) {
                throw new BusinessRuleViolationException("Đang có lệnh chi PayOS (" + o.getReferenceId()
                        + ") chưa có kết quả; hãy chờ kết quả (hoặc bấm gửi lại) thay vì chuyển tay để tránh chuyển tiền hai lần");
            }
        });
    }

    // ── khởi tạo lệnh chi ────────────────────────────────────────────────

    /** Chi tự động cho 1 yêu cầu rút ĐÃ DUYỆT của giảng viên. */
    public PayosDto.OrderRes transferInstructorPayout(String adminEmail, Long payoutId) {
        requireEnabled();
        Source src = new TransactionTemplate(txManager).execute(s -> loadPayoutSource(payoutId));
        return transfer(PayosOrderKind.INSTRUCTOR_PAYOUT, payoutId, adminEmail, src);
    }

    /** Chi tự động cho 1 khoản hoàn tiền ĐANG CHỜ CHUYỂN (đã được admin duyệt hoàn). */
    public PayosDto.OrderRes transferRefund(String adminEmail, Long disputeId) {
        requireEnabled();
        Source src = new TransactionTemplate(txManager).execute(s -> loadRefundSource(disputeId));
        return transfer(PayosOrderKind.DISPUTE_REFUND, disputeId, adminEmail, src);
    }

    private Source loadPayoutSource(Long payoutId) {
        InstructorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw new BusinessRuleViolationException("Chỉ chi tự động cho yêu cầu đã duyệt (trạng thái hiện tại: " + payout.getStatus() + ")");
        }
        var bank = BankDirectory.find(payout.getBankCode())
                .orElseThrow(() -> new BusinessRuleViolationException("Không xác định được ngân hàng của yêu cầu này; hãy chuyển thủ công"));
        String number = accountService.decryptForAdmin(payout.getAccountNumberEnc());
        return new Source(payout.getAmount().longValue(), bank.bin(), number, payout.getHolderName(), "LinguaLearn rut tien");
    }

    private Source loadRefundSource(Long disputeId) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        PaymentRefund refund = refundRepository.findByPayment_Id(dispute.getPayment().getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Khiếu nại này chưa được duyệt hoàn tiền"));
        if (refund.getTransferStatus() != RefundTransferStatus.PENDING_TRANSFER) {
            throw new BusinessRuleViolationException("Khoản hoàn này không còn ở trạng thái chờ chuyển");
        }
        // Ưu tiên tài khoản khai báo CÓ CẤU TRÚC và đã xác nhận chính chủ; khiếu nại cũ (chữ tự do) thì đọc chữ, mơ hồ thì từ chối.
        if (dispute.getRefundBankCode() != null && Boolean.TRUE.equals(dispute.getRefundAccountConfirmed())) {
            var bank = BankDirectory.find(dispute.getRefundBankCode())
                    .orElseThrow(() -> new BusinessRuleViolationException("Ngân hàng nhận hoàn tiền không hợp lệ; hãy chuyển thủ công"));
            return new Source(refund.getAmount().longValue(), bank.bin(), dispute.getRefundAccountNumber(),
                    dispute.getRefundAccountHolder(), "LinguaLearn hoan tien");
        }
        ParsedAccount parsed = parseRefundAccount(refund.getRefundBankInfo());
        return new Source(refund.getAmount().longValue(), parsed.bankBin(), parsed.number(), null, "LinguaLearn hoan tien");
    }

    record ParsedAccount(String bankBin, String number) {}

    /**
     * Đọc "MBBANK 0356307125" thành (BIN, số tài khoản). Chỉ chấp nhận khi RÕ RÀNG (đúng 1 dãy số 6–20 chữ số + tên ngân hàng khớp chính
     * xác); mơ hồ thì từ chối để chuyển thủ công — chi tự động mà đoán nhầm là rủi ro mất tiền.
     */
    static ParsedAccount parseRefundAccount(String raw) {
        String fail = "Không đọc được ngân hàng/số tài khoản người nhận hoàn tiền; hãy kiểm tra và chuyển thủ công";
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleViolationException(fail);
        }
        Matcher m = ACCOUNT_NUMBER.matcher(raw);
        if (!m.find()) {
            throw new BusinessRuleViolationException(fail);
        }
        int start = m.start();
        int end = m.end();
        String number = m.group(1).replace(" ", "");
        if (m.find() || number.length() < 6 || number.length() > 20) {
            throw new BusinessRuleViolationException(fail);
        }
        String bankText = (raw.substring(0, start) + " " + raw.substring(end)).trim();
        var bank = BankDirectory.findByText(bankText)
                .orElseThrow(() -> new BusinessRuleViolationException(fail));
        return new ParsedAccount(bank.bin(), number);
    }

    private PayosDto.OrderRes transfer(PayosOrderKind kind, Long entityId, String adminEmail, Source src) {
        if (src.amount() <= 0) {
            throw new BusinessRuleViolationException("Số tiền chi không hợp lệ");
        }
        if (src.amount() > maxAmount()) {
            throw new BusinessRuleViolationException("Số tiền vượt trần chi tự động (" + maxAmount() + "đ); hãy chuyển thủ công");
        }
        User admin = userRepository.findByEmail(adminEmail).orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        Optional<PayosPayoutOrder> latest = orderRepository.findFirstByKindAndEntityIdOrderByAttemptDesc(kind, entityId);
        PayosPayoutOrder order;
        boolean fresh;
        if (latest.isPresent()) {
            PayosOrderStatus st = latest.get().getStatus();
            if (st == PayosOrderStatus.SUCCEEDED) {
                throw new BusinessRuleViolationException("Lệnh chi này đã thành công trước đó");
            }
            if (IN_FLIGHT.contains(st)) {
                throw new BusinessRuleViolationException("Lệnh chi này đang được PayOS xử lý, vui lòng chờ kết quả");
            }
            // UNKNOWN: gửi lại với CÙNG referenceId (PayOS trả lại đúng lệnh cũ, không chi lần 2); FAILED: lệnh mới.
            fresh = st == PayosOrderStatus.FAILED;
            order = fresh ? newOrder(kind, entityId, latest.get().getAttempt() + 1, src, admin) : latest.get();
        } else {
            fresh = true;
            order = newOrder(kind, entityId, 1, src, admin);
        }
        TransferRequest req = new TransferRequest(order.getReferenceId(), src.amount(), src.description(), src.bankBin(), src.accountNumber());

        if (fresh || order.getId() == null) {
            checkFunds(req);
            order = orderRepository.save(order);
        } else {
            order.setStatus(PayosOrderStatus.PENDING);
            order = orderRepository.save(order);
        }
        auditService.record(adminEmail, "PAYOS_PAYOUT_SUBMITTING", "payos_payout_order", String.valueOf(order.getId()), null,
                orderSnapshot(order));

        try {
            applyResult(order, gateway.create(req), adminEmail);
        } catch (APIException e) {
            int status = e.getStatusCode().orElse(0);
            if (status >= 400 && status < 500) {
                markFailed(order, e.getErrorCode().orElse(String.valueOf(status)), e.getErrorDesc().orElse(safeMessage(e)));
            } else {
                markUnknown(order, safeMessage(e));
            }
        } catch (RuntimeException e) {
            markUnknown(order, safeMessage(e));
        }
        return toRes(orderRepository.findById(order.getId()).orElse(order));
    }

    private PayosPayoutOrder newOrder(PayosOrderKind kind, Long entityId, int attempt, Source src, User admin) {
        PayosPayoutOrder o = new PayosPayoutOrder();
        o.setKind(kind);
        o.setEntityId(entityId);
        o.setAttempt(attempt);
        o.setReferenceId((kind == PayosOrderKind.INSTRUCTOR_PAYOUT ? "LLP" : "LLR") + entityId + "-" + attempt);
        o.setAmount(src.amount());
        o.setBankBin(src.bankBin());
        o.setAccountLast4(src.last4());
        o.setHolderNameEntered(src.holderName());
        o.setStatus(PayosOrderStatus.PENDING);
        o.setCreatedByUserId(admin.getId());
        return o;
    }

    /** Số dư tài khoản chi phải đủ (gồm phí) — không đủ thì dừng NGAY, chưa tạo lệnh. */
    private void checkFunds(TransferRequest req) {
        long needed;
        long balance;
        try {
            needed = gateway.estimateCredit(req);
            balance = gateway.balance();
        } catch (RuntimeException e) {
            throw new BusinessRuleViolationException("Không kiểm tra được số dư tài khoản chi PayOS: " + safeMessage(e));
        }
        if (balance < needed) {
            throw new BusinessRuleViolationException("Số dư tài khoản chi PayOS không đủ (cần " + needed + "đ, hiện có " + balance
                    + "đ). Hãy nạp thêm hoặc chuyển thủ công.");
        }
    }

    // ── xử lý kết quả ────────────────────────────────────────────────────

    private void applyResult(PayosPayoutOrder order, TransferResult res, String actorEmail) {
        if (order.getStatus() == PayosOrderStatus.SUCCEEDED) {
            return; // đối soát và gửi lại chạy trùng nhau: đã chốt thì không làm lại
        }
        order.setPayosPayoutId(res.payoutId());
        order.setPayosState(res.txState() != null ? res.txState() : res.approvalState());
        order.setResolvedAccountName(res.resolvedAccountName());
        order.setBankReference(res.bankReference());
        order.setErrorCode(res.errorCode());
        order.setErrorMessage(truncate(res.errorMessage(), 500));

        String tx = res.txState();
        String approval = res.approvalState();
        if ("SUCCEEDED".equals(tx)) {
            order.setStatus(PayosOrderStatus.SUCCEEDED);
            order.setCompletedAt(LocalDateTime.now());
            orderRepository.save(order);
            onSucceeded(order, actorEmail);
        } else if (Set.of("FAILED", "CANCELLED", "REVERSED").contains(String.valueOf(tx))
                || Set.of("REJECTED", "CANCELLED", "FAILED").contains(String.valueOf(approval))) {
            markFailed(order, res.errorCode(), res.errorMessage() == null ? "PayOS báo " + order.getPayosState() : res.errorMessage());
        } else {
            order.setStatus(PayosOrderStatus.SUBMITTED);
            orderRepository.save(order);
        }
    }

    private void onSucceeded(PayosPayoutOrder order, String actorEmail) {
        String ref = "PAYOS-" + (order.getBankReference() != null && !order.getBankReference().isBlank()
                ? order.getBankReference() : order.getPayosPayoutId());
        String email = actorEmail != null ? actorEmail : creatorEmail(order);
        auditService.record(email, "PAYOS_PAYOUT_SUCCEEDED", "payos_payout_order", String.valueOf(order.getId()), null, orderSnapshot(order));
        try {
            if (order.getKind() == PayosOrderKind.INSTRUCTOR_PAYOUT) {
                payoutService.markPaid(email, order.getEntityId(), ref, null);
            } else {
                disputeService.markTransferred(email, order.getEntityId(), new DisputeDto.MarkTransferredReq(ref));
            }
        } catch (RuntimeException e) {
            // Tiền ĐÃ chuyển nhưng ghi nhận nội bộ lỗi: báo admin ghi nhận tay với đúng mã này (không để mất dấu).
            log.error("PayOS đã chi lệnh {} nhưng ghi nhận nội bộ lỗi: {}", order.getReferenceId(), e.getMessage());
            notifyAdmins("PAYOS_RECORD_FAILED", "PayOS đã chi nhưng chưa ghi nhận được",
                    "Lệnh " + order.getReferenceId() + " đã chuyển thành công (mã " + ref + ") nhưng hệ thống chưa ghi nhận: "
                            + safeMessage(e) + ". Hãy ghi nhận thủ công với mã này.", adminLink(order));
        }
        if (order.getResolvedAccountName() != null && order.getHolderNameEntered() != null
                && !NameNormalizer.matches(order.getResolvedAccountName(), order.getHolderNameEntered())) {
            notifyAdmins("PAYOS_NAME_MISMATCH", "Tên chủ tài khoản ngân hàng khác tên đã nhập",
                    "Lệnh " + order.getReferenceId() + ": ngân hàng trả về \"" + order.getResolvedAccountName()
                            + "\", khác tên đã nhập. Hãy kiểm tra xem tiền có đến đúng người không.", adminLink(order));
        }
    }

    private void markFailed(PayosPayoutOrder order, String code, String message) {
        order.setStatus(PayosOrderStatus.FAILED);
        order.setErrorCode(code);
        order.setErrorMessage(truncate(message, 500));
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        auditService.record(creatorEmail(order), "PAYOS_PAYOUT_FAILED", "payos_payout_order", String.valueOf(order.getId()), null,
                orderSnapshot(order));
        notifyAdmins("PAYOS_PAYOUT_FAILED", "Chi tự động thất bại",
                "Lệnh " + order.getReferenceId() + ": " + (message == null ? "PayOS từ chối" : message)
                        + ". Yêu cầu vẫn giữ nguyên trạng thái; hãy kiểm tra số tài khoản/ngân hàng rồi thử lại hoặc chuyển thủ công.",
                adminLink(order));
    }

    private void markUnknown(PayosPayoutOrder order, String message) {
        order.setStatus(PayosOrderStatus.UNKNOWN);
        order.setErrorMessage(truncate(message, 500));
        orderRepository.save(order);
        log.warn("PayOS payout {} không rõ kết quả: {}", order.getReferenceId(), message);
        notifyAdmins("PAYOS_PAYOUT_UNKNOWN", "Chi tự động chưa rõ kết quả",
                "Lệnh " + order.getReferenceId() + " chưa nhận được kết quả từ PayOS (" + message
                        + "). Có thể bấm chi lại — dùng cùng mã lệnh nên không chi trùng.", adminLink(order));
    }

    // ── đối soát nền ─────────────────────────────────────────────────────

    /** Hỏi PayOS kết quả các lệnh đã gửi nhưng chưa chốt (PayOS xử lý bất đồng bộ). */
    @Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    public void reconcileOpenOrders() {
        List<PayosPayoutOrder> open;
        try {
            open = orderRepository.findByStatusIn(List.of(PayosOrderStatus.SUBMITTED, PayosOrderStatus.UNKNOWN));
        } catch (RuntimeException e) {
            log.warn("Đối soát PayOS: không đọc được danh sách lệnh: {}", e.getMessage());
            return;
        }
        for (PayosPayoutOrder o : open) {
            if (o.getPayosPayoutId() == null) {
                continue; // chưa có mã PayOS để hỏi: chờ admin bấm gửi lại
            }
            try {
                PayosPayoutOrder fresh = orderRepository.findById(o.getId()).orElse(o);
                applyResult(fresh, gateway.get(fresh.getPayosPayoutId()), null);
            } catch (RuntimeException e) {
                log.warn("Đối soát PayOS lệnh {} lỗi: {}", o.getReferenceId(), safeMessage(e));
            }
        }
    }

    // ── tiện ích ─────────────────────────────────────────────────────────

    private void requireEnabled() {
        if (!gateway.isEnabled()) {
            throw new BusinessRuleViolationException("Chi tự động qua PayOS đang tắt; hãy chuyển khoản thủ công hoặc bật trong Cài đặt");
        }
    }

    private long maxAmount() {
        BigDecimal v = config.getDecimal(KEY_MAX_AMOUNT, BigDecimal.valueOf(DEFAULT_MAX_AMOUNT));
        return v == null ? DEFAULT_MAX_AMOUNT : v.longValue();
    }

    private String creatorEmail(PayosPayoutOrder order) {
        return order.getCreatedByUserId() == null ? null
                : userRepository.findById(order.getCreatedByUserId()).map(User::getEmail).orElse(null);
    }

    private void notifyAdmins(String type, String title, String content, String link) {
        userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), type, title, content, link));
    }

    private static String adminLink(PayosPayoutOrder o) {
        return o.getKind() == PayosOrderKind.INSTRUCTOR_PAYOUT ? "/admin/transactions/payouts" : "/admin/transactions/disputes";
    }

    private static Map<String, Object> orderSnapshot(PayosPayoutOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", o.getKind().name());
        m.put("entityId", o.getEntityId());
        m.put("reference", o.getReferenceId());
        m.put("amount", o.getAmount());
        m.put("last4", o.getAccountLast4());
        m.put("status", o.getStatus().name());
        return m;
    }

    private static String safeMessage(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : truncate(m, 300);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    static PayosDto.OrderRes toRes(PayosPayoutOrder o) {
        boolean mismatch = o.getResolvedAccountName() != null && o.getHolderNameEntered() != null
                && !NameNormalizer.matches(o.getResolvedAccountName(), o.getHolderNameEntered());
        return new PayosDto.OrderRes(o.getId(), o.getKind(), o.getEntityId(), o.getAttempt(), o.getReferenceId(), o.getAmount(),
                o.getAccountLast4(), o.getStatus(), o.getPayosState(), o.getResolvedAccountName(), o.getHolderNameEntered(),
                mismatch, o.getErrorMessage(), o.getCreatedAt(), o.getCompletedAt());
    }
}
