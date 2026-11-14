package com.lms.finance.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.LedgerEntryType;
import com.lms.payment.entity.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Một dòng sổ cái thu nhập giảng viên (11/11/2026, Epic hoàn tiền – chi trả). Chỉ thêm dòng
 * ({@code @Immutable}) — sửa sai bằng dòng đảo (REFUND_REVERSAL/ADJUSTMENT), không sửa dòng cũ.
 * {@code UNIQUE (payment_id, entry_type)} ở DB làm khóa idempotency: ghi lại cùng 1 đơn nhiều
 * lần không nhân đôi tiền. Xem V157.
 */
@Entity
@Immutable
@Table(name = "instructor_ledger_entries")
@Getter
@Setter
public class InstructorLedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    /** Có dấu: cộng vào số dư nếu dương, trừ nếu âm. */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Từ thời điểm này khoản được tính vào số dư KHẢ DỤNG (trước đó là "đang giữ"). */
    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    /** Gắn với yêu cầu rút (Giai đoạn C); chưa có khóa ngoại. */
    @Column(name = "payout_id")
    private Long payoutId;

    /** Có giá trị ở dòng DEBT_REPAYMENT — duy nhất, chống ghi trùng một lần trả nợ. */
    @Column(name = "debt_payment_id")
    private Long debtPaymentId;

    /** Dòng gốc mà dòng đảo này bù trừ (vd REFUND_REVERSAL → EARNING). */
    @Column(name = "ref_entry_id")
    private Long refEntryId;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;
}
