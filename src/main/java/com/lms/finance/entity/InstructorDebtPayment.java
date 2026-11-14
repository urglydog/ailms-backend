package com.lms.finance.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.DebtPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Một lần giảng viên trả nợ qua PayOS (V166); trả một phần được. */
@Entity
@Table(name = "instructor_debt_payments")
@Getter
@Setter
public class InstructorDebtPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DebtPaymentStatus status = DebtPaymentStatus.PENDING;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "payment_link_id", length = 64)
    private String paymentLinkId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
