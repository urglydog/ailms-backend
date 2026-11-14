package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RefundReasonCode;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.util.CacheEvictionHelper;
import com.lms.enrollment.service.CourseReviewService;
import com.lms.enrollment.service.EnrollmentService;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentRefundRepository;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hoàn tiền thật cho 1 giao dịch (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn B). Trước đây
 * duyệt hoàn tiền chỉ là đổi trạng thái khiếu nại: giao dịch vẫn PAID, học viên vẫn giữ khóa, doanh thu
 * giảng viên không giảm, có thể hoàn 2 lần. Giờ MỘT transaction DB làm đủ: đánh dấu REFUNDED, thu hồi quyền
 * học (mềm), thu hồi chứng chỉ, ẩn đánh giá, ghi sổ cái, tạo hồ sơ hoàn tiền, xóa cache, nhật ký kiểm toán.
 *
 * <p>Học viên luôn được hoàn 100% {@code payment.amount}; phần giảng viên phải trả lại do admin chọn
 * ({@link RefundResponsibility}): STANDARD = đúng phần giảng viên đã nhận; PLATFORM_COMPENSATES = 0
 * (nền tảng gánh hết); INSTRUCTOR_FAULT = toàn bộ số tiền hoàn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** Ngưỡng cảnh báo lạm dụng: số lần hoàn tiền của 1 học viên trong 90 ngày. */
    static final int ABUSE_REFUND_COUNT = 3;
    static final int ABUSE_WINDOW_DAYS = 90;

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final EnrollmentService enrollmentService;
    private final CourseReviewService courseReviewService;
    private final LedgerService ledgerService;
    private final FinanceAuditService financeAuditService;
    private final CacheEvictionHelper cacheEvictionHelper;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final com.lms.common.service.SystemConfigService systemConfigService;

    /** Phần giảng viên phải trả lại theo chế độ trách nhiệm (không âm). */
    public static BigDecimal instructorCharge(RefundResponsibility responsibility, Payment payment) {
        return switch (responsibility) {
            case STANDARD -> payment.getInstructorEarning() == null ? BigDecimal.ZERO : payment.getInstructorEarning();
            case PLATFORM_COMPENSATES -> BigDecimal.ZERO;
            case INSTRUCTOR_FAULT -> payment.getAmount();
        };
    }

    /**
     * Duyệt hoàn tiền. Idempotent: nếu giao dịch đã có hồ sơ hoàn tiền thì trả lại hồ sơ đó, không làm gì
     * thêm (khiếu nại được mở lại rồi duyệt lần 2 không hoàn tiền 2 lần).
     */
    @Transactional
    public PaymentRefund approve(PaymentDispute dispute, String adminEmail, RefundResponsibility responsibility,
                                 RefundReasonCode reasonCode, String note, String evidenceNote) {
        RefundResponsibility resp = responsibility == null ? RefundResponsibility.STANDARD : responsibility;
        Long paymentId = dispute.getPayment().getId();

        var existing = refundRepository.findByPayment_Id(paymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessRuleViolationException("Chỉ hoàn tiền được cho giao dịch đã thanh toán thành công");
        }
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new BusinessRuleViolationException("Giao dịch 0đ không có gì để hoàn");
        }
        validatePolicy(dispute, resp, reasonCode, note, evidenceNote, LocalDateTime.now());
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        LocalDateTime now = LocalDateTime.now();
        BigDecimal charge = instructorCharge(resp, payment);
        BigDecimal platformShare = payment.getAmount().subtract(charge);

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(now);
        paymentRepository.save(payment);

        enrollmentService.revokeForRefund(payment, "Hoàn tiền giao dịch " + payment.getTxnRef());
        courseReviewService.hideForRefund(payment.getUser().getId(), payment.getCourse().getId());
        ledgerService.recordRefundReversal(payment, charge, admin.getId());

        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setDispute(dispute);
        refund.setAmount(payment.getAmount());
        refund.setResponsibility(resp);
        refund.setInstructorCharge(charge);
        refund.setPlatformShare(platformShare);
        refund.setRefundBankInfo(dispute.getRefundBankAccount());
        refund.setTransferStatus(RefundTransferStatus.PENDING_TRANSFER);
        refund.setApprovedBy(admin);
        refund.setApprovedAt(now);
        refund.setNote(note);
        refund.setReasonCode(reasonCode);
        refund.setEvidenceNote(evidenceNote == null || evidenceNote.isBlank() ? null : evidenceNote.trim());
        PaymentRefund saved = refundRepository.save(refund);

        cacheEvictionHelper.evictCourseCacheAfterCommit(payment.getCourse().getSlug());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "REFUNDED");
        after.put("amount", payment.getAmount());
        after.put("responsibility", resp.name());
        after.put("instructorCharge", charge);
        after.put("platformShare", platformShare);
        after.put("disputeId", dispute.getId());
        financeAuditService.record(adminEmail, "REFUND_APPROVED", "payment", payment.getTxnRef(),
                Map.of("status", "PAID"), after);

        notifyInstructor(payment, charge, resp, reasonCode, note, evidenceNote);
        flagSuspectedAbuse(payment.getUser(), now);
        return saved;
    }

    /**
     * Admin đã chuyển khoản tay cho học viên: ghi mã giao dịch, chuyển TRANSFERRED, báo học viên. Idempotent
     * khi gọi lại với hồ sơ đã TRANSFERRED (báo lỗi rõ ràng, không ghi đè mã cũ).
     */
    @Transactional
    public PaymentRefund markTransferred(PaymentDispute dispute, String adminEmail, String transferRef) {
        PaymentRefund refund = refundRepository.findByPayment_Id(dispute.getPayment().getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Khiếu nại này chưa được duyệt hoàn tiền"));
        if (refund.getTransferStatus() == RefundTransferStatus.TRANSFERRED) {
            throw new BusinessRuleViolationException("Khoản hoàn này đã được ghi nhận chuyển tiền trước đó");
        }
        if (transferRef == null || transferRef.isBlank()) {
            throw new BusinessRuleViolationException("Cần nhập mã giao dịch chuyển khoản");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));
        refund.setTransferStatus(RefundTransferStatus.TRANSFERRED);
        refund.setTransferRef(transferRef.trim());
        refund.setTransferredBy(admin);
        refund.setTransferredAt(LocalDateTime.now());
        PaymentRefund saved = refundRepository.save(refund);

        Payment payment = dispute.getPayment();
        financeAuditService.record(adminEmail, "REFUND_TRANSFERRED", "payment", payment.getTxnRef(),
                Map.of("transferStatus", "PENDING_TRANSFER"),
                Map.of("transferStatus", "TRANSFERRED", "transferRef", saved.getTransferRef()));
        notificationService.notify(dispute.getFiledBy().getId(), "REFUND_TRANSFERRED",
                "Đã chuyển tiền hoàn cho bạn",
                "Giao dịch " + payment.getTxnRef() + " đã được hoàn tiền (mã chuyển khoản " + saved.getTransferRef() + ").",
                "/payments");
        emailService.sendRefundTransferredEmail(dispute.getFiledBy().getEmail(), payment.getTxnRef(),
                payment.getCourse().getTitle(), payment.getAmount(), saved.getTransferRef());
        return saved;
    }

    /**
     * Chính sách chọn chế độ trách nhiệm (14/11/2026): mỗi mã lý do chỉ đi với đúng 1 chế độ; chế độ khác Chuẩn bắt buộc có mã lý do; "giảng
     * viên chịu toàn bộ" còn cần ghi chú lý do, minh chứng, và giảng viên đã có cơ hội phản hồi (đã phản hồi hoặc đã hết hạn phản hồi).
     */
    static void validatePolicy(PaymentDispute dispute, RefundResponsibility resp, RefundReasonCode code, String note,
                               String evidenceNote, LocalDateTime now) {
        if (code != null && code.responsibility() != resp) {
            throw new BusinessRuleViolationException("Mã lý do " + code.name() + " chỉ dùng cho chế độ " + code.responsibility().name());
        }
        if (resp != RefundResponsibility.STANDARD && code == null) {
            throw new BusinessRuleViolationException("Cần chọn mã lý do cho chế độ này");
        }
        if (resp == RefundResponsibility.INSTRUCTOR_FAULT) {
            if (note == null || note.trim().length() < 10) {
                throw new BusinessRuleViolationException("Chế độ \"Giảng viên chịu\" bắt buộc ghi lý do rõ ràng (tối thiểu 10 ký tự)");
            }
            if (evidenceNote == null || evidenceNote.trim().length() < 20) {
                throw new BusinessRuleViolationException("Chế độ \"Giảng viên chịu\" bắt buộc nêu minh chứng (tối thiểu 20 ký tự: bằng chứng nào, ở đâu)");
            }
            LocalDateTime deadline = dispute.getInstructorDeadlineAt();
            boolean heardOut = dispute.getInstructorDecision() != null || deadline == null || !deadline.isAfter(now);
            if (!heardOut) {
                throw new BusinessRuleViolationException("Giảng viên chưa phản hồi và chưa hết hạn phản hồi; hãy chờ rồi mới quyết định giảng viên chịu trách nhiệm");
            }
        }
    }

    static String reasonLabel(RefundReasonCode code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case BUYER_REMORSE -> "học viên đổi ý trong hạn";
            case OTHER -> "lý do khác";
            case TECHNICAL_ISSUE -> "lỗi kỹ thuật từ phía hệ thống";
            case DOUBLE_CHARGE -> "thanh toán trùng";
            case NOT_ENROLLED -> "đã trừ tiền nhưng không ghi danh";
            case PLATFORM_FAULT -> "lỗi từ phía nền tảng";
            case COPYRIGHT_VIOLATION -> "vi phạm bản quyền";
            case CONTENT_MISMATCH -> "nội dung sai khác nghiêm trọng so với mô tả";
            case PROHIBITED_CONTENT -> "nội dung bị cấm";
        };
    }

    private void notifyInstructor(Payment payment, BigDecimal charge, RefundResponsibility resp, RefundReasonCode code,
                                  String note, String evidenceNote) {
        User instructor = payment.getCourse().getInstructor();
        if (instructor == null) {
            return;
        }
        StringBuilder text = new StringBuilder("Giao dịch " + payment.getTxnRef() + " khóa \"" + payment.getCourse().getTitle() + "\" đã hoàn tiền. ");
        text.append(charge.signum() > 0
                ? "Thu nhập của bạn bị trừ " + charge.toBigInteger() + "đ."
                : "Thu nhập của bạn không bị trừ (nền tảng chịu).");
        if (code != null) {
            text.append(" Lý do: ").append(reasonLabel(code)).append('.');
        }
        if (resp == RefundResponsibility.INSTRUCTOR_FAULT) {
            text.append(" Ghi chú của quản trị viên: ").append(note == null ? "" : note.trim()).append('.');
            text.append(" Minh chứng: ").append(evidenceNote == null ? "" : evidenceNote.trim()).append('.');
            text.append(" Bạn có ").append(appealDays()).append(" ngày để khiếu nại quyết định này trong mục Hoàn tiền.");
        }
        notificationService.notify(instructor.getId(), "REFUND_APPROVED_INSTRUCTOR",
                "Một giao dịch khóa học của bạn đã được hoàn tiền", text.toString(), "/instructor/communication/refunds");
    }

    private int appealDays() {
        return systemConfigService.getInt(com.lms.finance.FinanceConfigKeys.DISPUTE_APPEAL_DAYS, 7);
    }

    private void flagSuspectedAbuse(User learner, LocalDateTime now) {
        long recent = refundRepository.countByPayment_User_IdAndApprovedAtAfter(
                learner.getId(), now.minusDays(ABUSE_WINDOW_DAYS));
        if (recent >= ABUSE_REFUND_COUNT) {
            userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(),
                    "REFUND_ABUSE_SUSPECT", "Học viên hoàn tiền nhiều lần",
                    learner.getFullName() + " đã được hoàn tiền " + recent + " lần trong " + ABUSE_WINDOW_DAYS + " ngày.",
                    "/admin/transactions/disputes"));
        }
    }
}
