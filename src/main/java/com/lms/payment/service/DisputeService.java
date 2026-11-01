package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.common.storage.StorageService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.DisputeAttachment;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.DisputeAttachmentRepository;
import com.lms.payment.repository.PaymentDisputeRepository;
import com.lms.payment.repository.PaymentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Khiếu nại/hoàn tiền giao dịch (23/10/2026) — trước đây nút "Hoàn tiền"/"Khiếu nại" ở trang
 * Đối soát giao dịch của Admin chỉ là toast, không gọi API nào. Mở rộng (cùng ngày, chuyên
 * nghiệp hóa) — cho đính kèm minh chứng ảnh/video, bắt buộc xác nhận điều khoản, gửi email
 * formal khi admin xử lý xong. Vẫn tối giản có chủ đích: không tích hợp cổng thanh toán thật,
 * Admin chỉ đánh dấu đã hoàn tiền (xem docblock {@link PaymentDispute}).
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    /** MIME thật (Tika) -> đuôi file hợp lệ — cùng khuôn với LessonDocumentService, mở rộng thêm ảnh/video. */
    private static final Map<String, String> ALLOWED_MIME_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "video/mp4", "mp4",
            "video/webm", "webm"
    );

    private final PaymentDisputeRepository disputeRepository;
    private final DisputeAttachmentRepository attachmentRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final StorageService storageService;
    private final Tika tika = new Tika();

    @Value("${lms.rules.max-dispute-evidence-files}")
    private int maxEvidenceFiles;

    @Value("${lms.rules.max-dispute-evidence-size-mb}")
    private long maxEvidenceSizeMb;

    @Value("${lms.rules.dispute-reopen-window-days}")
    private int reopenWindowDays;

    @Transactional
    public DisputeDto.Res fileDispute(String email, DisputeDto.FileReq req, List<MultipartFile> files) {
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
        if (!req.termsAccepted()) {
            throw new BusinessRuleViolationException("Bạn phải đồng ý với chính sách hoàn tiền trước khi gửi khiếu nại");
        }

        List<MultipartFile> evidenceFiles = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (evidenceFiles.size() > maxEvidenceFiles) {
            throw new BusinessRuleViolationException("Chỉ được đính kèm tối đa " + maxEvidenceFiles + " file minh chứng");
        }

        PaymentDispute dispute = new PaymentDispute();
        dispute.setPayment(payment);
        dispute.setFiledBy(user);
        dispute.setReason(req.reason());
        dispute.setDescription(req.description());
        dispute.setRefundBankAccount(req.refundBankAccount());
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setTermsAcceptedAt(LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);

        for (MultipartFile file : evidenceFiles) {
            attachmentRepository.save(uploadEvidence(saved, file));
        }

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

    private DisputeAttachment uploadEvidence(PaymentDispute dispute, MultipartFile file) {
        long maxBytes = maxEvidenceSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleViolationException("File minh chứng vượt quá " + maxEvidenceSizeMb + "MB");
        }

        String detectedMime;
        try (InputStream sniff = file.getInputStream()) {
            detectedMime = tika.detect(sniff);
        } catch (IOException e) {
            throw new InvalidRequestException("Không đọc được file minh chứng: " + e.getMessage());
        }
        String extension = ALLOWED_MIME_TO_EXTENSION.get(detectedMime);
        if (extension == null) {
            throw new InvalidRequestException("Định dạng minh chứng không được hỗ trợ (chỉ nhận ảnh jpg/png/webp hoặc video mp4/webm)");
        }

        String key = "dispute-evidence/" + dispute.getId() + "/" + UUID.randomUUID() + "." + extension;
        String url;
        try (InputStream in = file.getInputStream()) {
            url = storageService.upload(key, in, file.getSize(), detectedMime);
        } catch (IOException e) {
            throw new InvalidRequestException("Không tải được minh chứng lên kho lưu trữ: " + e.getMessage());
        }

        DisputeAttachment attachment = new DisputeAttachment();
        attachment.setDispute(dispute);
        attachment.setFileUrl(url);
        attachment.setFileType(extension);
        attachment.setFileSize(file.getSize());
        return attachment;
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
        if (dispute.getStatus() != DisputeStatus.OPEN && dispute.getStatus() != DisputeStatus.REOPENED) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được xử lý trước đó");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        // Resolve lần 2 (sau reopen) — giữ lại note của lần resolve đầu trước khi bị ghi đè.
        if (dispute.getStatus() == DisputeStatus.REOPENED && dispute.getAdminNote() != null) {
            dispute.setPreviousAdminNote(dispute.getAdminNote());
        }

        dispute.setStatus(req.status());
        dispute.setAdminNote(req.note());
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);

        boolean refunded = req.status() == DisputeStatus.RESOLVED_REFUNDED;

        notificationService.notify(
                dispute.getFiledBy().getId(),
                "DISPUTE_RESOLVED",
                "Khiếu nại của bạn đã được xử lý",
                refunded
                        ? "Giao dịch " + dispute.getPayment().getTxnRef() + " đã được đánh dấu hoàn tiền."
                        : "Giao dịch " + dispute.getPayment().getTxnRef() + " không được chấp nhận hoàn tiền.",
                "/payments"
        );

        // (23/10/2026) — trước đây kết quả xử lý CHỈ báo qua notification trong app, user không
        // nhận được gì qua email. 1 nút submit duy nhất của admin giờ tự gửi email formal luôn,
        // không cần thao tác thủ công nào thêm.
        emailService.sendDisputeResolutionEmail(
                dispute.getFiledBy().getEmail(),
                refunded,
                dispute.getPayment().getTxnRef(),
                dispute.getPayment().getCourse().getTitle(),
                dispute.getPayment().getAmount(),
                dispute.getReason(),
                req.note(),
                dispute.getRefundBankAccount()
        );

        return toAdminRes(saved);
    }

    /** User báo "chưa nhận được tiền" sau khi admin đã RESOLVED_REFUNDED (26/10/2026) — chỉ
     * cho phép khi đã hoàn tiền (không áp dụng cho RESOLVED_REJECTED, đó là quyết định từ chối
     * chứ không phải lỗi hoàn tiền), trong hạn {@link #reopenWindowDays} ngày, và chỉ 1 lần. */
    @Transactional
    public DisputeDto.Res reopen(String email, Long disputeId, DisputeDto.ReopenReq req) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!dispute.getFiledBy().getId().equals(user.getId())) {
            throw new BusinessRuleViolationException("Bạn chỉ có thể mở lại khiếu nại của chính mình");
        }
        if (dispute.getStatus() != DisputeStatus.RESOLVED_REFUNDED) {
            throw new BusinessRuleViolationException("Chỉ có thể mở lại khiếu nại đã được đánh dấu hoàn tiền");
        }
        if (dispute.getReopenedAt() != null) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được mở lại trước đó, vui lòng liên hệ hỗ trợ trực tiếp");
        }
        if (dispute.getResolvedAt() == null
                || dispute.getResolvedAt().plusDays(reopenWindowDays).isBefore(LocalDateTime.now())) {
            throw new BusinessRuleViolationException(
                    "Đã quá hạn " + reopenWindowDays + " ngày để mở lại khiếu nại này, vui lòng liên hệ hỗ trợ trực tiếp");
        }

        dispute.setStatus(DisputeStatus.REOPENED);
        dispute.setReopenReason(req.reopenReason());
        dispute.setReopenedAt(LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);

        userRepository.findByRole(Role.ADMIN).forEach(admin ->
                notificationService.notify(
                        admin.getId(),
                        "DISPUTE_REOPENED",
                        "⚠️ Khiếu nại bị MỞ LẠI — ưu tiên xử lý",
                        user.getFullName() + " báo CHƯA NHẬN ĐƯỢC TIỀN cho giao dịch "
                                + dispute.getPayment().getTxnRef() + " (đã đánh dấu hoàn tiền trước đó)",
                        "/admin/transactions/disputes"
                ));

        return toRes(saved);
    }

    private List<DisputeDto.AttachmentRes> toAttachmentRes(PaymentDispute d) {
        return attachmentRepository.findByDispute_IdOrderByIdAsc(d.getId()).stream()
                .map(a -> new DisputeDto.AttachmentRes(a.getId(), a.getFileUrl(), a.getFileType()))
                .toList();
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
                d.getCreatedAt(),
                toAttachmentRes(d),
                d.getResolvedAt(),
                d.getReopenedAt()
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
                d.getCreatedAt(),
                toAttachmentRes(d),
                d.getReopenReason(),
                d.getReopenedAt(),
                d.getPreviousAdminNote()
        );
    }
}
