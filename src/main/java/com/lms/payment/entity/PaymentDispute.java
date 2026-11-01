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
}
