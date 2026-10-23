package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khiếu nại/hoàn tiền giao dịch (23/10/2026) — trước đây nút "Hoàn tiền"/"Khiếu nại" ở trang
 * Đối soát giao dịch của Admin chỉ là toast, không gọi API nào. Tối giản có chủ đích: không
 * tích hợp cổng thanh toán thật, Admin chỉ đánh dấu đã hoàn tiền (xem docblock
 * {@link PaymentDispute}).
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final PaymentDisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public DisputeDto.Res fileDispute(String email, DisputeDto.FileReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Payment payment = paymentRepository.findByTxnRef(req.paymentTxnRef())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", req.paymentTxnRef()));

        if (!payment.getUser().getId().equals(user.getId())) {
            throw new BusinessRuleViolationException("Bạn chỉ có thể khiếu nại giao dịch của chính mình");
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessRuleViolationException("Chỉ có thể khiếu nại giao dịch đã thanh toán thành công");
        }
        disputeRepository.findByPayment_IdAndStatus(payment.getId(), DisputeStatus.OPEN)
                .ifPresent(d -> {
                    throw new BusinessRuleViolationException("Giao dịch này đang có khiếu nại chưa xử lý");
                });

        PaymentDispute dispute = new PaymentDispute();
        dispute.setPayment(payment);
        dispute.setFiledBy(user);
        dispute.setReason(req.reason());
        dispute.setDescription(req.description());
        dispute.setRefundBankAccount(req.refundBankAccount());
        dispute.setStatus(DisputeStatus.OPEN);
        PaymentDispute saved = disputeRepository.save(dispute);

        userRepository.findByRole(Role.ADMIN).forEach(admin ->
                notificationService.notify(
                        admin.getId(),
                        "DISPUTE_FILED",
                        "Khiếu nại giao dịch mới",
                        user.getFullName() + " khiếu nại giao dịch " + payment.getTxnRef(),
                        "/admin/transactions/disputes"
                ));

        return toRes(saved);
    }

    @Transactional(readOnly = true)
    public List<DisputeDto.Res> getMine(String email) {
        return disputeRepository.findByFiledBy_EmailOrderByCreatedAtDesc(email).stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<DisputeDto.AdminRes> getAllForAdmin(DisputeStatus status) {
        List<PaymentDispute> disputes = status == null
                ? disputeRepository.findAllByOrderByCreatedAtDesc()
                : disputeRepository.findByStatusOrderByCreatedAtDesc(status);
        return disputes.stream().map(this::toAdminRes).toList();
    }

    @Transactional
    public DisputeDto.AdminRes resolve(String adminEmail, Long disputeId, DisputeDto.ResolveReq req) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được xử lý trước đó");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        dispute.setStatus(req.status());
        dispute.setAdminNote(req.note());
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(java.time.LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);

        notificationService.notify(
                dispute.getFiledBy().getId(),
                "DISPUTE_RESOLVED",
                "Khiếu nại của bạn đã được xử lý",
                req.status() == DisputeStatus.RESOLVED_REFUNDED
                        ? "Giao dịch " + dispute.getPayment().getTxnRef() + " đã được đánh dấu hoàn tiền."
                        : "Giao dịch " + dispute.getPayment().getTxnRef() + " không được chấp nhận hoàn tiền.",
                "/payments"
        );

        return toAdminRes(saved);
    }

    private DisputeDto.Res toRes(PaymentDispute d) {
        return new DisputeDto.Res(
                d.getId(),
                d.getPayment().getTxnRef(),
                d.getPayment().getCourse().getTitle(),
                d.getPayment().getAmount(),
                d.getReason(),
                d.getDescription(),
                d.getStatus(),
                d.getFiledBy().getEmail(),
                d.getCreatedAt()
        );
    }

    private DisputeDto.AdminRes toAdminRes(PaymentDispute d) {
        return new DisputeDto.AdminRes(
                d.getId(),
                d.getPayment().getTxnRef(),
                d.getPayment().getCourse().getTitle(),
                d.getPayment().getAmount(),
                d.getReason(),
                d.getDescription(),
                d.getStatus(),
                d.getFiledBy().getEmail(),
                d.getRefundBankAccount(),
                d.getAdminNote(),
                d.getResolvedBy() != null ? d.getResolvedBy().getEmail() : null,
                d.getResolvedAt(),
                d.getCreatedAt()
        );
    }
}
