package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.AttachmentSide;
import com.lms.common.enums.DisputeCategory;
import com.lms.common.enums.DisputeEligibility;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.service.SystemConfigService;
import com.lms.enrollment.entity.Enrollment;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.finance.FinanceConfigKeys;
import com.lms.payment.entity.PaymentRefund;
import com.lms.payment.repository.PaymentRefundRepository;
import java.math.BigDecimal;
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
    private final RefundService refundService;
    private final RefundPolicyService refundPolicyService;
    private final EnrollmentRepository enrollmentRepository;
    private final SystemConfigService systemConfigService;
    private final PaymentRefundRepository refundRepository;
    private final com.lms.finance.payos.PayosOrderGuard payosOrderGuard;
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
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new BusinessRuleViolationException("Giao dịch 0đ không có gì để hoàn tiền");
        }
        // 11/11/2026 — mỗi giao dịch chỉ có MỘT khiếu nại (trước đây chỉ chặn khi đang OPEN → sau khi xử lý
        // vẫn nộp lại được và có thể hoàn tiền 2 lần). Đã xử lý xong mà cần xem lại thì dùng "mở lại" hoặc hỗ trợ.
        if (disputeRepository.existsByPayment_Id(payment.getId())) {
            throw new BusinessRuleViolationException("Giao dịch này đã có khiếu nại, không thể gửi thêm");
        }
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
        applyRefundAccount(dispute, req, user);
        dispute.setStatus(DisputeStatus.OPEN);
        LocalDateTime now = LocalDateTime.now();
        dispute.setTermsAcceptedAt(now);
        DisputeCategory category = req.category() == null ? DisputeCategory.OTHER : req.category();
        dispute.setCategory(category);
        BigDecimal progress = enrollmentRepository.findByUser_IdAndCourse_Id(user.getId(), payment.getCourse().getId())
                .map(Enrollment::getProgressPct).orElse(BigDecimal.ZERO);
        dispute.setProgressPctAtFiling(progress);
        dispute.setEligibility(refundPolicyService.evaluate(category, payment.getPaidAt(), progress, now));
        dispute.setInstructorDeadlineAt(now.plusHours(systemConfigService.getInt(
                FinanceConfigKeys.DISPUTE_INSTRUCTOR_RESPONSE_HOURS, 48)));
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

        // Giảng viên của khóa được báo ngay và có hạn để phản hồi (trước đây chỉ admin biết).
        User instructor = payment.getCourse().getInstructor();
        if (instructor != null && !instructor.getId().equals(user.getId())) {
            notificationService.notify(
                    instructor.getId(),
                    "DISPUTE_FILED_INSTRUCTOR",
                    "Học viên gửi yêu cầu hoàn tiền khóa học của bạn",
                    user.getFullName() + " khiếu nại giao dịch " + payment.getTxnRef() + " (khóa \""
                            + payment.getCourse().getTitle() + "\"). Vui lòng phản hồi trước hạn.",
                    "/instructor/communication/refunds"
            );
        }

        return toRes(saved);
    }

    /** Đính kèm minh chứng vào khiếu nại đã có (giảng viên phản hồi). Dùng chung quy tắc kiểm tra file với học viên. */
    @Transactional
    public void attachEvidence(PaymentDispute dispute, List<MultipartFile> files, AttachmentSide side) {
        List<MultipartFile> evidenceFiles = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (evidenceFiles.size() > maxEvidenceFiles) {
            throw new BusinessRuleViolationException("Chỉ được đính kèm tối đa " + maxEvidenceFiles + " file minh chứng");
        }
        for (MultipartFile file : evidenceFiles) {
            DisputeAttachment attachment = uploadEvidence(dispute, file);
            attachment.setSide(side);
            attachmentRepository.save(attachment);
        }
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
        if (req.status() != DisputeStatus.RESOLVED_REFUNDED && req.status() != DisputeStatus.RESOLVED_REJECTED) {
            throw new BusinessRuleViolationException("Trạng thái xử lý không hợp lệ: chỉ nhận đã hoàn tiền hoặc từ chối");
        }
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
        if (refunded) {
            // Hoàn tiền THẬT (11/11/2026): REFUNDED + thu hồi ghi danh + sổ cái... trong cùng transaction.
            // Lỗi ở đây (vd giao dịch không còn PAID) làm rollback cả việc đổi trạng thái khiếu nại.
            refundService.approve(saved, adminEmail, req.responsibility(), req.reasonCode(), req.note(), req.evidenceNote());
        } else {
            User instructor = dispute.getPayment().getCourse().getInstructor();
            if (instructor != null) {
                notificationService.notify(instructor.getId(), "DISPUTE_REJECTED_INSTRUCTOR",
                        "Yêu cầu hoàn tiền khóa học của bạn đã được từ chối",
                        "Giao dịch " + dispute.getPayment().getTxnRef() + " không được hoàn tiền; thu nhập của bạn không bị ảnh hưởng.",
                        "/instructor/communication/refunds");
            }
        }

        notificationService.notify(
                dispute.getFiledBy().getId(),
                "DISPUTE_RESOLVED",
                "Khiếu nại của bạn đã được xử lý",
                refunded
                        ? "Giao dịch " + dispute.getPayment().getTxnRef() + " đã được chấp nhận hoàn tiền. Hệ thống sẽ chi trả khoản hoàn vào tài khoản bạn đã khai và báo lại khi hoàn tất."
                        : "Giao dịch " + dispute.getPayment().getTxnRef() + " không được chấp nhận hoàn tiền.",
                "/disputes/new"
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

    /** Admin ghi nhận đã chuyển khoản tiền hoàn cho học viên (11/11/2026). */
    @Transactional
    public DisputeDto.AdminRes markTransferred(String adminEmail, Long disputeId, DisputeDto.MarkTransferredReq req) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        refundService.markTransferred(dispute, adminEmail, req.transferRef());
        return toAdminRes(dispute);
    }

    private List<DisputeDto.AttachmentRes> toAttachmentRes(PaymentDispute d) {
        return attachmentRepository.findByDispute_IdOrderByIdAsc(d.getId()).stream()
                .map(a -> new DisputeDto.AttachmentRes(a.getId(), a.getFileUrl(), a.getFileType(), a.getSide()))
                .toList();
    }

    private DisputeDto.Res toRes(PaymentDispute d) {
        PaymentRefund refund = refundRepository.findByPayment_Id(d.getPayment().getId()).orElse(null);
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
                d.getReopenedAt(),
                d.getAdminNote(),
                d.getCategory(),
                d.getEligibility(),
                d.getInstructorDecision(),
                refund != null ? refund.getTransferStatus() : null,
                d.getPayment().getPaidAt() != null ? refundPolicyService.refundDeadline(d.getPayment().getPaidAt()) : null,
                accountEditability(d, refund).can(),
                accountEditability(d, refund).hint(),
                accountSummary(d)
        );
    }

    private DisputeDto.AdminRes toAdminRes(PaymentDispute d) {
        PaymentRefund refund = refundRepository.findByPayment_Id(d.getPayment().getId()).orElse(null);
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
                d.getPreviousAdminNote(),
                d.getCategory(),
                d.getEligibility(),
                d.getProgressPctAtFiling(),
                d.getInstructorDecision(),
                d.getInstructorResponse(),
                d.getInstructorRespondedAt(),
                d.getInstructorDeadlineAt(),
                d.getPayment().getInstructorEarning(),
                d.getPayment().getPlatformFee(),
                d.getPayment().getPaidAt(),
                refund != null ? refund.getResponsibility() : null,
                refund != null ? refund.getInstructorCharge() : null,
                refund != null ? refund.getPlatformShare() : null,
                refund != null ? refund.getTransferStatus() : null,
                refund != null ? refund.getTransferRef() : null,
                d.getRefundBankCode(),
                d.getRefundAccountNumber(),
                d.getRefundAccountHolder(),
                Boolean.TRUE.equals(d.getRefundAccountConfirmed()),
                refund != null ? refund.getReasonCode() : null,
                refund != null ? refund.getEvidenceNote() : null,
                refund != null ? refund.getAppealStatus() : null,
                refund != null ? refund.getAppealReason() : null,
                refund != null ? refund.getAppealAt() : null,
                refund != null ? refund.getAppealDecisionNote() : null,
                d.getRefundAccountUpdatedAt()
        );
    }

    /**
     * Ghi tài khoản nhận hoàn tiền. Có khai báo CÓ CẤU TRÚC (mới) thì phải đủ ngân hàng + số (6–20 chữ số) + tên chủ tài khoản và học viên phải
     * xác nhận đây là tài khoản CỦA CHÍNH MÌNH (trách nhiệm về độ chính xác thuộc người khai). Không có thì giữ ô chữ tự do cũ (client cũ).
     */
    private void applyRefundAccount(PaymentDispute dispute, DisputeDto.FileReq req, User user) {
        boolean structured = notBlank(req.refundBankCode()) || notBlank(req.refundAccountNumber()) || notBlank(req.refundAccountHolder());
        if (!structured) {
            dispute.setRefundBankAccount(req.refundBankAccount());
            return;
        }
        applyStructuredAccount(dispute, req.refundBankCode(), req.refundAccountNumber(), req.refundAccountHolder(), req.refundAccountOwnConfirmed());
    }

    /** Kiểm tra và ghi tài khoản nhận hoàn tiền có cấu trúc (dùng chung cho lúc gửi khiếu nại và lúc học viên cập nhật sau đó). */
    private void applyStructuredAccount(PaymentDispute dispute, String bankCode, String accountNumber, String accountHolder, Boolean ownConfirmed) {
        var bank = com.lms.finance.BankDirectory.find(bankCode)
                .orElseThrow(() -> new BusinessRuleViolationException("Ngân hàng nhận hoàn tiền không hợp lệ"));
        String number = accountNumber == null ? "" : accountNumber.replaceAll("\\s", "");
        if (!number.matches("[0-9]{6,20}")) {
            throw new BusinessRuleViolationException("Số tài khoản nhận hoàn tiền chỉ gồm chữ số (6–20 số)");
        }
        String holder = com.lms.finance.NameNormalizer.normalize(accountHolder);
        if (holder.isEmpty()) {
            throw new BusinessRuleViolationException("Cần nhập tên chủ tài khoản nhận hoàn tiền");
        }
        if (!Boolean.TRUE.equals(ownConfirmed)) {
            throw new BusinessRuleViolationException("Bạn cần xác nhận đây là tài khoản của chính bạn");
        }
        dispute.setRefundBankCode(bank.code());
        dispute.setRefundAccountNumber(number);
        dispute.setRefundAccountHolder(holder);
        dispute.setRefundAccountConfirmed(true);
        String shown = bank.name() + " " + number + " - " + holder;
        dispute.setRefundBankAccount(shown.length() > 100 ? shown.substring(0, 100) : shown);
    }

    // ── cập nhật tài khoản nhận hoàn tiền sau khi đã gửi khiếu nại (16/11/2026) ──────────────────────

    /** Kết quả đánh giá học viên có được sửa tài khoản nhận hoàn tiền lúc này không, kèm lời giải thích. */
    record AccountEditability(boolean can, String hint) {}

    /**
     * Khi nào học viên được/không được sửa tài khoản nhận hoàn tiền (BE quyết định, giao diện chỉ hiển thị theo):
     * <ul>
     *   <li>Khiếu nại chưa xử lý (OPEN/REOPENED): được — chưa dùng tới tài khoản.</li>
     *   <li>Bị từ chối: khóa — không có khoản hoàn nào cần tài khoản.</li>
     *   <li>Đã duyệt hoàn: được khi CHỜ CHUYỂN (chưa chi hoặc chi lỗi tạm thời) hoặc CHỜ CẬP NHẬT TÀI KHOẢN;
     *       khóa khi có lệnh PayOS đang xử lý (tiền có thể đang đi) hoặc đã chuyển xong.</li>
     * </ul>
     */
    AccountEditability accountEditability(PaymentDispute d, PaymentRefund refund) {
        switch (d.getStatus()) {
            case OPEN, REOPENED:
                return new AccountEditability(true, null);
            case RESOLVED_REJECTED:
                return new AccountEditability(false, "Khiếu nại không được hoàn tiền nên không cần tài khoản nhận.");
            default:
                break;
        }
        if (refund == null) {
            return new AccountEditability(false, null);
        }
        if (refund.getTransferStatus() == RefundTransferStatus.TRANSFERRED) {
            return new AccountEditability(false, "Khoản hoàn đã được chuyển.");
        }
        if (payosOrderGuard.hasUnsettled(com.lms.common.enums.PayosOrderKind.DISPUTE_REFUND, d.getId())) {
            return new AccountEditability(false, "Khoản hoàn đang được chuyển; vui lòng chờ kết quả rồi mới đổi tài khoản.");
        }
        if (refund.getTransferStatus() == RefundTransferStatus.AWAITING_ACCOUNT) {
            return new AccountEditability(true, "Chưa chuyển được vào tài khoản đã khai. Hãy kiểm tra và cập nhật tài khoản nhận.");
        }
        return new AccountEditability(true, null);
    }

    static String accountSummary(PaymentDispute d) {
        if (d.getRefundBankCode() == null || d.getRefundAccountNumber() == null) {
            return null;
        }
        String number = d.getRefundAccountNumber();
        String last4 = number.length() <= 4 ? number : number.substring(number.length() - 4);
        String bank = com.lms.finance.BankDirectory.find(d.getRefundBankCode()).map(b -> b.name()).orElse(d.getRefundBankCode());
        return bank + " ••••" + last4 + (d.getRefundAccountHolder() == null ? "" : " · " + d.getRefundAccountHolder());
    }

    /** Học viên cập nhật tài khoản nhận hoàn tiền của khiếu nại của chính mình. Quy tắc ở {@link #accountEditability}. */
    @Transactional
    public DisputeDto.Res updateRefundAccount(String email, Long disputeId, DisputeDto.UpdateRefundAccountReq req) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        if (!dispute.getFiledBy().getId().equals(user.getId())) {
            throw new BusinessRuleViolationException("Bạn chỉ cập nhật được tài khoản của khiếu nại của chính mình");
        }
        PaymentRefund refund = refundRepository.findByPayment_Id(dispute.getPayment().getId()).orElse(null);
        AccountEditability e = accountEditability(dispute, refund);
        if (!e.can()) {
            throw new BusinessRuleViolationException(e.hint() != null ? e.hint() : "Hiện không thể cập nhật tài khoản nhận hoàn tiền");
        }
        applyStructuredAccount(dispute, req.refundBankCode(), req.refundAccountNumber(), req.refundAccountHolder(), req.refundAccountOwnConfirmed());
        dispute.setRefundAccountUpdatedAt(LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);
        if (refund != null) {
            refundService.onRefundAccountUpdated(saved, refund);
        }
        return toRes(saved);
    }

    /** Admin yêu cầu học viên cập nhật tài khoản nhận hoàn tiền (lỗi chưa rõ nguyên nhân, hoặc đã hết lượt chi PayOS). */
    @Transactional
    public DisputeDto.AdminRes requestAccountUpdate(String actorEmail, Long disputeId, String reason) {
        PaymentDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId));
        refundService.awaitAccountUpdate(dispute, actorEmail, reason);
        return toAdminRes(dispute);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Hình giảng viên xem — KHÔNG có email/SĐT/STK ngân hàng/ghi chú nội bộ admin của học viên. */
    DisputeDto.InstructorRes toInstructorRes(PaymentDispute d) {
        PaymentRefund refund = refundRepository.findByPayment_Id(d.getPayment().getId()).orElse(null);
        return new DisputeDto.InstructorRes(
                d.getId(),
                d.getPayment().getTxnRef(),
                d.getPayment().getCourse().getId(),
                d.getPayment().getCourse().getTitle(),
                d.getFiledBy().getFullName(),
                d.getPayment().getAmount(),
                d.getPayment().getInstructorEarning(),
                d.getPayment().getPaidAt(),
                d.getReason(),
                d.getDescription(),
                d.getCategory(),
                d.getEligibility(),
                d.getProgressPctAtFiling(),
                d.getStatus(),
                d.getCreatedAt(),
                attachmentsBySide(d, AttachmentSide.STUDENT),
                attachmentsBySide(d, AttachmentSide.INSTRUCTOR),
                d.getInstructorDecision(),
                d.getInstructorResponse(),
                d.getInstructorRespondedAt(),
                d.getInstructorDeadlineAt(),
                d.getResolvedAt(),
                refund != null ? refund.getInstructorCharge() : null,
                refund != null ? refund.getResponsibility() : null,
                refund != null ? refund.getReasonCode() : null,
                refund != null ? refund.getNote() : null,
                refund != null ? refund.getEvidenceNote() : null,
                appealDeadline(refund),
                refund != null ? refund.getAppealStatus() : null,
                refund != null ? refund.getAppealReason() : null,
                refund != null ? refund.getAppealDecisionNote() : null
        );
    }

    /** Một khiếu nại ở dạng admin xem (dùng sau khi quyết định khiếu nại của giảng viên). */
    @Transactional(readOnly = true)
    public DisputeDto.AdminRes getAdmin(Long disputeId) {
        return toAdminRes(disputeRepository.findById(disputeId).orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", disputeId)));
    }

    /** Hạn khiếu nại: chỉ khi quyết định là "giảng viên chịu toàn bộ" và chưa khiếu nại. */
    private LocalDateTime appealDeadline(PaymentRefund refund) {
        if (refund == null || refund.getResponsibility() != com.lms.common.enums.RefundResponsibility.INSTRUCTOR_FAULT
                || refund.getAppealStatus() != com.lms.common.enums.AppealStatus.NONE || refund.getApprovedAt() == null) {
            return null;
        }
        return refund.getApprovedAt().plusDays(systemConfigService.getInt(FinanceConfigKeys.DISPUTE_APPEAL_DAYS, 7));
    }

    private List<DisputeDto.AttachmentRes> attachmentsBySide(PaymentDispute d, AttachmentSide side) {
        return attachmentRepository.findByDispute_IdAndSideOrderByIdAsc(d.getId(), side).stream()
                .map(a -> new DisputeDto.AttachmentRes(a.getId(), a.getFileUrl(), a.getFileType(), a.getSide()))
                .toList();
    }
}
