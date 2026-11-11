package com.lms.payment.service;

import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.AttachmentSide;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.payment.dto.DisputeDto;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.PaymentDisputeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Giảng viên quản lý khiếu nại/yêu cầu hoàn tiền thuộc CÁC KHÓA CỦA MÌNH (11/11/2026, Epic hoàn tiền –
 * chi trả, Giai đoạn B). Trước đây mọi khiếu nại chỉ tới admin, giảng viên không biết gì và không có tiếng
 * nói. Giảng viên chỉ PHẢN HỒI (đồng ý hoàn / phản đối + minh chứng); admin vẫn là người quyết định cuối.
 * Mọi dữ liệu cá nhân của học viên (email, SĐT, STK ngân hàng) KHÔNG được trả về — xem
 * {@code DisputeService#toInstructorRes}.
 */
@Service
@RequiredArgsConstructor
public class InstructorDisputeService {

    private static final Set<InstructorDecision> ALLOWED_DECISIONS =
            Set.of(InstructorDecision.AGREE_REFUND, InstructorDecision.OBJECT);
    private static final Set<DisputeStatus> OPEN_STATUSES = Set.of(DisputeStatus.OPEN, DisputeStatus.REOPENED);

    private final PaymentDisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final DisputeService disputeService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<DisputeDto.InstructorRes> list(String instructorEmail, DisputeStatus status, Long courseId) {
        return disputeRepository.findByPayment_Course_Instructor_EmailOrderByCreatedAtDesc(instructorEmail).stream()
                .filter(d -> status == null || d.getStatus() == status)
                .filter(d -> courseId == null || d.getPayment().getCourse().getId().equals(courseId))
                .map(disputeService::toInstructorRes)
                .toList();
    }

    @Transactional(readOnly = true)
    public DisputeDto.InstructorRes get(String instructorEmail, Long id) {
        return disputeService.toInstructorRes(requireOwned(instructorEmail, id));
    }

    @Transactional
    public DisputeDto.InstructorRes respond(String instructorEmail, Long id, DisputeDto.InstructorRespondReq req,
                                            List<MultipartFile> files) {
        PaymentDispute dispute = requireOwned(instructorEmail, id);

        if (!ALLOWED_DECISIONS.contains(req.decision())) {
            throw new BusinessRuleViolationException("Quyết định không hợp lệ: chỉ nhận đồng ý hoàn tiền hoặc phản đối");
        }
        if (!OPEN_STATUSES.contains(dispute.getStatus())) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được admin xử lý, không thể phản hồi nữa");
        }
        // Phản hồi 1 lần; phản hồi muộn (sau khi job đánh dấu NO_RESPONSE) vẫn nhận nếu admin chưa quyết định.
        if (dispute.getInstructorDecision() != null && dispute.getInstructorDecision() != InstructorDecision.NO_RESPONSE) {
            throw new BusinessRuleViolationException("Bạn đã phản hồi khiếu nại này rồi");
        }

        dispute.setInstructorDecision(req.decision());
        dispute.setInstructorResponse(req.response().trim());
        dispute.setInstructorRespondedAt(LocalDateTime.now());
        PaymentDispute saved = disputeRepository.save(dispute);
        disputeService.attachEvidence(saved, files, AttachmentSide.INSTRUCTOR);

        userRepository.findByRole(Role.ADMIN).forEach(admin -> notificationService.notify(
                admin.getId(),
                "DISPUTE_INSTRUCTOR_RESPONDED",
                "Giảng viên đã phản hồi khiếu nại",
                "Giao dịch " + dispute.getPayment().getTxnRef() + ": giảng viên "
                        + (req.decision() == InstructorDecision.AGREE_REFUND ? "ĐỒNG Ý hoàn tiền" : "PHẢN ĐỐI hoàn tiền") + ".",
                "/admin/transactions/disputes"));

        return disputeService.toInstructorRes(saved);
    }

    private PaymentDispute requireOwned(String instructorEmail, Long id) {
        PaymentDispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentDispute", id));
        var instructor = dispute.getPayment().getCourse().getInstructor();
        if (instructor == null || !instructor.getEmail().equals(instructorEmail)) {
            throw new AccessDeniedDomainException("Bạn chỉ xem được khiếu nại thuộc khóa học của chính mình");
        }
        return dispute;
    }
}
