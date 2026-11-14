package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.AppealStatus;
import com.lms.common.enums.RefundReasonCode;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.FinanceConfigKeys;
import com.lms.finance.debt.DebtService;
import com.lms.finance.service.FinanceAuditService;
import com.lms.finance.service.LedgerService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khiếu nại của giảng viên với quyết định hoàn tiền "giảng viên chịu toàn bộ" (14/11/2026): một lần, trong hạn
 * {@code DISPUTE_APPEAL_DAYS} kể từ lúc duyệt hoàn tiền. Trong lúc chờ, chế tài công nợ tạm dừng (xem {@code DebtGuard}). Admin giữ nguyên
 * hoặc giảm về chế độ Chuẩn — khi giảm thì hoàn lại phần phạt vượt mức vào sổ cái.
 */
@Service
@RequiredArgsConstructor
public class RefundAppealService {

    private final PaymentDisputeRepository disputeRepository;
    private final PaymentRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FinanceAuditService auditService;
    private final LedgerService ledgerService;
    private final DebtService debtService;
    private final SystemConfigService config;

    @Transactional
    public void appeal(String instructorEmail, Long disputeId, String reason) {
        PaymentDispute dispute = disputeRepository.findById(disputeId).orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        User instructor = dispute.getPayment().getCourse().getInstructor();
        if (instructor == null || !instructor.getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn chỉ khiếu nại được quyết định thuộc khóa học của mình");
        }
        PaymentRefund refund = refundRepository.findByPayment_Id(dispute.getPayment().getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Giao dịch này chưa có quyết định hoàn tiền"));
        if (refund.getResponsibility() != RefundResponsibility.INSTRUCTOR_FAULT) {
            throw new BusinessRuleViolationException("Chỉ khiếu nại được quyết định \"giảng viên chịu toàn bộ\"");
        }
        if (refund.getAppealStatus() != AppealStatus.NONE) {
            throw new BusinessRuleViolationException("Bạn đã khiếu nại quyết định này rồi (mỗi quyết định khiếu nại một lần)");
        }
        LocalDateTime now = LocalDateTime.now();
        int days = config.getInt(FinanceConfigKeys.DISPUTE_APPEAL_DAYS, 7);
        if (refund.getApprovedAt() == null || refund.getApprovedAt().plusDays(days).isBefore(now)) {
            throw new BusinessRuleViolationException("Đã quá hạn khiếu nại (" + days + " ngày kể từ khi duyệt hoàn tiền)");
        }
        if (reason == null || reason.trim().length() < 20) {
            throw new BusinessRuleViolationException("Hãy nêu lý do khiếu nại rõ ràng (tối thiểu 20 ký tự)");
        }
        refund.setAppealStatus(AppealStatus.OPEN);
        refund.setAppealReason(reason.trim());
        refund.setAppealAt(now);
        refundRepository.save(refund);

        auditService.record(instructorEmail, "REFUND_APPEAL_OPENED", "payment", dispute.getPayment().getTxnRef(), null,
                Map.of("disputeId", disputeId));
        userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(), "REFUND_APPEAL",
                "Giảng viên khiếu nại quyết định hoàn tiền",
                instructor.getFullName() + " khiếu nại quyết định giao dịch " + dispute.getPayment().getTxnRef() + ".",
                "/admin/transactions/disputes"));
    }

    @Transactional
    public void decide(String adminEmail, Long disputeId, DisputeDto.AppealDecision decision, String note) {
        PaymentDispute dispute = disputeRepository.findById(disputeId).orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        PaymentRefund refund = refundRepository.findByPayment_Id(dispute.getPayment().getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Giao dịch này chưa có quyết định hoàn tiền"));
        if (refund.getAppealStatus() != AppealStatus.OPEN) {
            throw new BusinessRuleViolationException("Không có khiếu nại nào đang chờ xử lý cho giao dịch này");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleViolationException("Cần nhập lý do quyết định");
        }
        User admin = userRepository.findByEmail(adminEmail).orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));
        Payment payment = dispute.getPayment();
        User instructor = payment.getCourse().getInstructor();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("decision", decision.name());

        if (decision == DisputeDto.AppealDecision.REDUCE_TO_STANDARD) {
            BigDecimal standard = payment.getInstructorEarning() == null ? BigDecimal.ZERO : payment.getInstructorEarning();
            BigDecimal oldCharge = refund.getInstructorCharge() == null ? BigDecimal.ZERO : refund.getInstructorCharge();
            BigDecimal give = oldCharge.subtract(standard);
            if (give.signum() > 0 && instructor != null) {
                ledgerService.recordAdjustment(instructor, give,
                        "Hoàn lại phần phạt vượt mức (khiếu nại thành công) đơn " + payment.getTxnRef(), admin.getId());
                after.put("refundedToInstructor", give);
            }
            refund.setResponsibility(RefundResponsibility.STANDARD);
            refund.setInstructorCharge(standard);
            refund.setPlatformShare(refund.getAmount().subtract(standard));
            refund.setReasonCode(RefundReasonCode.OTHER);
            refund.setAppealStatus(AppealStatus.REDUCED);
        } else {
            refund.setAppealStatus(AppealStatus.UPHELD);
        }
        refund.setAppealDecisionNote(note.trim());
        refund.setAppealDecidedAt(LocalDateTime.now());
        refundRepository.save(refund);

        auditService.record(adminEmail, "REFUND_APPEAL_DECIDED", "payment", payment.getTxnRef(), null, after);
        if (instructor != null) {
            boolean reduced = decision == DisputeDto.AppealDecision.REDUCE_TO_STANDARD;
            notificationService.notify(instructor.getId(), "REFUND_APPEAL_RESULT",
                    reduced ? "Khiếu nại của bạn được chấp nhận" : "Khiếu nại của bạn không được chấp nhận",
                    "Giao dịch " + payment.getTxnRef() + ": " + (reduced
                            ? "quyết định được giảm về chế độ Chuẩn, phần phạt vượt mức đã được hoàn lại vào số dư của bạn."
                            : "giữ nguyên quyết định.") + " Lý do: " + note.trim(),
                    "/instructor/communication/refunds");
            debtService.refreshState(instructor.getId());
        }
    }
}
