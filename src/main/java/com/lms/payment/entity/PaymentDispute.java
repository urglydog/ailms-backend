package com.lms.payment.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.DisputeStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Khiếu nại của học viên/giảng viên về 1 giao dịch thanh toán (UC chưa đặc tả gốc, bổ sung
 * 23/10/2026 — trước đó trang "Đối soát giao dịch" của Admin có nút Hoàn tiền/Khiếu nại nhưng
 * không gọi API nào cả, chỉ hiện toast).
 *
 * <p>Tối giản có chủ đích: không có bảng log/audit-trail riêng, chỉ 1 field {@code adminNote}
 * — đủ dùng cho v1, không tích hợp cổng thanh toán thật khi hoàn tiền (chỉ đánh dấu
 * {@link #status} = RESOLVED_REFUNDED + set {@link #resolvedAt}).
 *
 * <p>Reopen (26/10/2026) — thanh toán qua QR nên hệ thống không có sẵn thông tin tài khoản
 * người mua, {@link #refundBankAccount} do chính user tự nhập lúc khiếu nại; Admin hoàn tiền
 * thủ công ngoài hệ thống nên không có cách nào hệ thống tự xác nhận tiền đã tới tay user.
 * Nếu user báo "chưa nhận được tiền" sau khi đã RESOLVED_REFUNDED, có thể mở lại đúng 1 lần
 * trong vòng {@code lms.rules.dispute-reopen-window-days} ngày — xem DisputeService#reopen.
 * {@link #previousAdminNote} snapshot lại {@link #adminNote} của lần resolve đầu trước khi bị
 * ghi đè bởi lần resolve thứ 2, để không mất thông tin xử lý trước đó.
 */
@Entity
@Table(name = "payment_disputes")
@Getter
@Setter
public class PaymentDispute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filed_by_user_id", nullable = false)
    private User filedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status = DisputeStatus.OPEN;

    /** STK nhận hoàn tiền, học viên tự nhập lúc gửi khiếu nại — Admin hoàn tiền thủ công ngoài hệ thống. */
    @Column(name = "refund_bank_account", length = 100)
    private String refundBankAccount;

    /** Tài khoản nhận hoàn tiền khai báo có cấu trúc (V165) — dùng cho chi tự động; null với khiếu nại cũ/client cũ. */
    @Column(name = "refund_bank_code", length = 20)
    private String refundBankCode;

    @Column(name = "refund_account_number", length = 30)
    private String refundAccountNumber;

    @Column(name = "refund_account_holder", length = 100)
    private String refundAccountHolder;

    /** Học viên đã xác nhận đây là tài khoản của chính mình. */
    @Column(name = "refund_account_confirmed", nullable = false)
    private Boolean refundAccountConfirmed = false;

    /** Lần cuối học viên cập nhật tài khoản nhận hoàn tiền SAU khi gửi khiếu nại; mốc để tính lại số lần chi PayOS thử lại. */
    @Column(name = "refund_account_updated_at")
    private LocalDateTime refundAccountUpdatedAt;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** Bắt buộc tick điều khoản trước khi gửi (23/10/2026) — chỉ lưu "đã tick lúc nào", không
     * snapshot nội dung điều khoản (trang /legal/refund là nguồn sự thật duy nhất). */
    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    @OneToMany(mappedBy = "dispute", fetch = FetchType.LAZY)
    private List<DisputeAttachment> attachments;

    /** Lý do user nêu khi mở lại khiếu nại — null nếu chưa bao giờ reopen. */
    @Column(name = "reopen_reason", columnDefinition = "TEXT")
    private String reopenReason;

    /** Thời điểm user bấm "chưa nhận được tiền". != null nghĩa là ĐÃ dùng hết lượt reopen
     * (tối đa 1 lần theo thiết kế tối giản — không cần cột đếm riêng). */
    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    /** Snapshot {@link #adminNote} của lần resolve đầu, chụp lại trước khi bị ghi đè bởi lần
     * resolve thứ 2 (sau khi dispute bị reopen). */
    @Column(name = "previous_admin_note", columnDefinition = "TEXT")
    private String previousAdminNote;

    // ── Khiếu nại v2 (11/11/2026, V160) — các cột NULL ở 3 khiếu nại cũ ───────────────────────

    /** Lý do chuẩn hóa; null ở khiếu nại cũ. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private com.lms.common.enums.DisputeCategory category;

    /** Tính lúc gửi (trong hạn + tiến độ ≤ ngưỡng...). Admin vẫn quyết định cuối. */
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility", length = 20)
    private com.lms.common.enums.DisputeEligibility eligibility;

    /** Tiến độ học (%) của học viên lúc gửi khiếu nại. */
    @Column(name = "progress_pct_at_filing", precision = 5, scale = 2)
    private java.math.BigDecimal progressPctAtFiling;

    @Enumerated(EnumType.STRING)
    @Column(name = "instructor_decision", length = 20)
    private com.lms.common.enums.InstructorDecision instructorDecision;

    @Column(name = "instructor_response", columnDefinition = "TEXT")
    private String instructorResponse;

    @Column(name = "instructor_responded_at")
    private LocalDateTime instructorRespondedAt;

    /** Hạn để giảng viên phản hồi; quá hạn mà chưa phản hồi → NO_RESPONSE (job quét). */
    @Column(name = "instructor_deadline_at")
    private LocalDateTime instructorDeadlineAt;
}
