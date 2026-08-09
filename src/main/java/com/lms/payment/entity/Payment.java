package com.lms.payment.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Giao dịch thanh toán khóa học (UC13, UC14, UC48).
 *
 * <p><b>BR-PAY-02 — quan trọng nhất:</b> {@code amount} lấy từ giá khóa học
 * <b>trên server</b> tại thời điểm tạo đơn. TUYỆT ĐỐI không nhận số tiền do client
 * gửi lên.
 *
 * <p><b>BR-PAY-03:</b> {@code txnRef} UNIQUE là khóa <b>idempotency</b> của callback
 * IPN — cổng thanh toán gửi lại callback thành công lần thứ hai phải bị nhận diện và
 * bỏ qua, không được tạo Enrollment thứ hai.
 *
 * <p><b>BR-PAY-05:</b> {@code platformFee} (30%) và {@code instructorEarning} (70%)
 * được tính và <b>chốt cứng tại thời điểm PAID</b>, không tính lại lúc hiển thị —
 * để số liệu thống kê không lệch nếu tỷ lệ % thay đổi trong tương lai.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends BaseEntity {

    /** Mã đơn hàng duy nhất — khóa idempotency của callback IPN (BR-PAY-03). */
    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;

    /** Lấy từ giá server, KHÔNG nhận từ client (BR-PAY-02). */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** = amount x 30%, chốt cứng lúc PAID (BR-PAY-05). */
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee = BigDecimal.ZERO;

    /** = amount x 70%, chốt cứng lúc PAID (BR-PAY-05). */
    @Column(name = "instructor_earning", nullable = false, precision = 12, scale = 2)
    private BigDecimal instructorEarning = BigDecimal.ZERO;

    /** MOMO / ZALOPAY / VNPAY (đều Sandbox). */
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Mã giao dịch do cổng thanh toán trả về. */
    @Column(name = "gateway_txn_no", length = 100)
    private String gatewayTxnNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "billing_name", length = 100)
    private String billingName;

    @Column(name = "billing_phone", length = 20)
    private String billingPhone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
