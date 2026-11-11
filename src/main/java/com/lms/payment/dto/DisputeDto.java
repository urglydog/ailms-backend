package com.lms.payment.dto;

import com.lms.common.enums.AttachmentSide;
import com.lms.common.enums.DisputeCategory;
import com.lms.common.enums.DisputeEligibility;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.RefundTransferStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO khiếu nại/hoàn tiền. (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn B) các trường mới đều THÊM
 * Ở CUỐI record và optional ở request → client cũ (mobile đã phát hành) vẫn chạy: bỏ qua trường lạ ở
 * response, không gửi trường mới ở request (category mặc định OTHER, responsibility mặc định STANDARD).
 */
public class DisputeDto {

    public record FileReq(
            @NotBlank String paymentTxnRef,
            @NotBlank String reason,
            String description,
            String refundBankAccount,
            @AssertTrue(message = "Bạn phải đồng ý với chính sách hoàn tiền trước khi gửi khiếu nại")
            boolean termsAccepted,
            /** Lý do chuẩn hóa — null (client cũ) được hiểu là OTHER. */
            DisputeCategory category
    ) {}

    public record AttachmentRes(Long id, String fileUrl, String fileType, AttachmentSide side) {}

    /** Học viên xem khiếu nại của chính mình. */
    public record Res(
            Long id,
            String paymentTxnRef,
            String courseTitle,
            BigDecimal amount,
            String reason,
            String description,
            DisputeStatus status,
            String filedByEmail,
            LocalDateTime createdAt,
            List<AttachmentRes> attachments,
            LocalDateTime resolvedAt,
            LocalDateTime reopenedAt,
            /** Lý do/phản hồi của admin — trước đây học viên không thấy vì sao khiếu nại bị từ chối. */
            String adminNote,
            DisputeCategory category,
            DisputeEligibility eligibility,
            /** Quyết định của giảng viên (không lộ nội dung phản hồi riêng). */
            InstructorDecision instructorDecision,
            /** null nếu chưa duyệt hoàn tiền; PENDING_TRANSFER = đã duyệt, chờ chuyển khoản. */
            RefundTransferStatus refundTransferStatus,
            /** Hạn cuối được xin hoàn tiền của giao dịch (để hiển thị cho học viên). */
            LocalDateTime refundDeadline
    ) {}

    public record AdminRes(
            Long id,
            String paymentTxnRef,
            String courseTitle,
            BigDecimal amount,
            String reason,
            String description,
            DisputeStatus status,
            String filedByEmail,
            String refundBankAccount,
            String adminNote,
            String resolvedByEmail,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            List<AttachmentRes> attachments,
            String reopenReason,
            LocalDateTime reopenedAt,
            String previousAdminNote,
            DisputeCategory category,
            DisputeEligibility eligibility,
            BigDecimal progressPctAtFiling,
            InstructorDecision instructorDecision,
            String instructorResponse,
            LocalDateTime instructorRespondedAt,
            LocalDateTime instructorDeadlineAt,
            /** Phần giảng viên/nền tảng ĐÃ nhận của giao dịch — để xem trước số tiền khi chọn chế độ trách nhiệm. */
            BigDecimal instructorEarning,
            BigDecimal platformFee,
            LocalDateTime paidAt,
            RefundResponsibility refundResponsibility,
            BigDecimal refundInstructorCharge,
            BigDecimal refundPlatformShare,
            RefundTransferStatus refundTransferStatus,
            String refundTransferRef
    ) {}

    /**
     * {@code responsibility} optional (null = STANDARD): STANDARD giảng viên trả lại đúng phần đã nhận;
     * PLATFORM_COMPENSATES nền tảng gánh hết; INSTRUCTOR_FAULT giảng viên trả toàn bộ (bắt buộc ghi lý do).
     */
    public record ResolveReq(
            @NotNull DisputeStatus status,
            @NotBlank String note,
            RefundResponsibility responsibility
    ) {}

    /** Admin đã chuyển khoản tiền hoàn cho học viên. */
    public record MarkTransferredReq(@NotBlank @Size(max = 100) String transferRef) {}

    /** Reopen (26/10/2026) — user báo "chưa nhận được tiền" sau khi đã RESOLVED_REFUNDED. */
    public record ReopenReq(
            @NotBlank String reopenReason
    ) {}

    /** Giảng viên xem khiếu nại thuộc khóa của mình — KHÔNG có email/SĐT/STK ngân hàng của học viên. */
    public record InstructorRes(
            Long id,
            String paymentTxnRef,
            Long courseId,
            String courseTitle,
            String learnerName,
            BigDecimal amount,
            BigDecimal instructorEarning,
            LocalDateTime paidAt,
            String reason,
            String description,
            DisputeCategory category,
            DisputeEligibility eligibility,
            BigDecimal progressPctAtFiling,
            DisputeStatus status,
            LocalDateTime createdAt,
            List<AttachmentRes> studentAttachments,
            List<AttachmentRes> instructorAttachments,
            InstructorDecision instructorDecision,
            String instructorResponse,
            LocalDateTime instructorRespondedAt,
            LocalDateTime instructorDeadlineAt,
            LocalDateTime resolvedAt,
            /** Số tiền bị trừ khỏi thu nhập của giảng viên nếu đã hoàn tiền (null nếu chưa/không hoàn). */
            BigDecimal instructorCharge
    ) {}

    public record InstructorRespondReq(
            /** Chỉ nhận AGREE_REFUND hoặc OBJECT. */
            @NotNull InstructorDecision decision,
            @NotBlank @Size(max = 4000) String response
    ) {}
}
