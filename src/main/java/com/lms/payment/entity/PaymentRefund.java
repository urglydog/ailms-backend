package com.lms.payment.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.RefundResponsibility;
import com.lms.common.enums.RefundTransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Hồ sơ hoàn tiền của 1 giao dịch (11/11/2026, Epic hoàn tiền – chi trả, V159). UNIQUE theo
 * {@code payment_id} → mỗi giao dịch chỉ hoàn được 1 lần ở tầng DB. Hai bước tách bạch: admin DUYỆT
 * (trạng thái {@code PENDING_TRANSFER}, quyền học bị thu hồi ngay) rồi admin CHUYỂN KHOẢN TAY và nhập
 * mã giao dịch ({@code TRANSFERRED}).
 */
@Entity
@Table(name = "payment_refunds")
@Getter
@Setter
public class PaymentRefund extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id")
    private PaymentDispute dispute;

    /** Số tiền hoàn cho học viên = payment.amount (luôn hoàn đủ 100%). */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility", nullable = false, length = 30)
    private RefundResponsibility responsibility;

    /** Phần giảng viên phải trả lại (đã ghi sổ cái dưới dạng REFUND_REVERSAL). */
    @Column(name = "instructor_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal instructorCharge;

    /** Phần nền tảng gánh = amount - instructorCharge. */
    @Column(name = "platform_share", nullable = false, precision = 14, scale = 2)
    private BigDecimal platformShare;

    /** STK nhận hoàn tiền do học viên tự nhập lúc khiếu nại (chụp lại để không đổi về sau). */
    @Column(name = "refund_bank_info", length = 100)
    private String refundBankInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private RefundTransferStatus transferStatus = RefundTransferStatus.PENDING_TRANSFER;

    @Column(name = "transfer_ref", length = 100)
    private String transferRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by_user_id", nullable = false)
    private User approvedBy;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_by_user_id")
    private User transferredBy;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    /** Mã lý do chuẩn (V167); null với hoàn tiền cũ. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "reason_code", length = 30)
    private com.lms.common.enums.RefundReasonCode reasonCode;

    /** Minh chứng admin dẫn ra khi chọn chế độ "giảng viên chịu toàn bộ" — giảng viên được xem. */
    @Column(name = "evidence_note", columnDefinition = "TEXT")
    private String evidenceNote;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "appeal_status", nullable = false, length = 20)
    private com.lms.common.enums.AppealStatus appealStatus = com.lms.common.enums.AppealStatus.NONE;

    @Column(name = "appeal_reason", columnDefinition = "TEXT")
    private String appealReason;

    @Column(name = "appeal_at")
    private LocalDateTime appealAt;

    @Column(name = "appeal_decision_note", columnDefinition = "TEXT")
    private String appealDecisionNote;

    @Column(name = "appeal_decided_at")
    private LocalDateTime appealDecidedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
