package com.lms.finance.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Yêu cầu rút tiền của giảng viên (V161). Chụp lại (snapshot) tài khoản nhận tiền lúc yêu cầu. Trạng thái đi qua
 * {@code PayoutService} (máy trạng thái): PENDING_EMAIL → REQUESTED → APPROVED → PAID; nhánh REJECTED/CANCELLED/FAILED
 * đều trả lại khoản giữ chỗ vào sổ cái.
 */
@Entity
@Table(name = "instructor_payouts")
@Getter
@Setter
public class InstructorPayout extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING_EMAIL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private InstructorPayoutAccount account;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number_enc", nullable = false, length = 255)
    private String accountNumberEnc;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(name = "email_confirmed_at")
    private LocalDateTime emailConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_user_id")
    private User paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "transfer_ref", length = 100)
    private String transferRef;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
