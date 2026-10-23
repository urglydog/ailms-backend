package com.lms.payment.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.DisputeStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
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
}
