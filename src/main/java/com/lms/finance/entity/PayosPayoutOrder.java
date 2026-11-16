package com.lms.finance.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.PayosFailureCategory;
import com.lms.common.enums.PayosOrderKind;
import com.lms.common.enums.PayosOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Một lệnh chi gửi sang PayOS (V164). Không lưu số tài khoản đầy đủ. */
@Entity
@Table(name = "payos_payout_orders")
@Getter
@Setter
public class PayosPayoutOrder extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private PayosOrderKind kind;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "attempt", nullable = false)
    private Integer attempt = 1;

    /** Cũng là khóa idempotency gửi PayOS. */
    @Column(name = "reference_id", nullable = false, length = 40, unique = true)
    private String referenceId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "bank_bin", nullable = false, length = 10)
    private String bankBin;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Column(name = "holder_name_entered", length = 100)
    private String holderNameEntered;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayosOrderStatus status = PayosOrderStatus.PENDING;

    @Column(name = "payos_payout_id", length = 64)
    private String payosPayoutId;

    @Column(name = "payos_state", length = 30)
    private String payosState;

    /** Tên chủ tài khoản do ngân hàng/PayOS trả về (để đối chiếu với tên đã nhập). */
    @Column(name = "resolved_account_name", length = 150)
    private String resolvedAccountName;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    /** Nhóm nguyên nhân khi FAILED (xem {@link PayosFailureCategory}); null với lệnh chưa/không thất bại. */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 20)
    private PayosFailureCategory failureCategory;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
