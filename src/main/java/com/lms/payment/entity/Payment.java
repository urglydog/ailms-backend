package com.lms.payment.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RevenueSource;
import com.lms.coupon.entity.Coupon;
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
 * <p><b>BR-PAY-05 (20/09/2026, thay bằng chia doanh thu 2 mức):</b> {@code platformFee}
 * và {@code instructorEarning} được tính và <b>chốt cứng tại thời điểm PAID</b>, không
 * tính lại lúc hiển thị — để số liệu thống kê không lệch nếu tỷ lệ % thay đổi trong tương
 * lai. Tỷ lệ áp dụng phụ thuộc {@link #revenueSource}, chốt SẴN lúc tạo đơn (trước PAID):
 * {@code ORGANIC} → Giảng viên 37% / nền tảng 63%; {@code INSTRUCTOR_REFERRAL} (mua qua
 * liên kết giới thiệu riêng của Giảng viên, {@code Course.referralCode}) → Giảng viên
 * 97% / nền tảng 3%. Xem {@code PaymentService.resolveRevenueSource}/{@code applyOutcome}.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends BaseEntity {

    /** Mã đơn hàng duy nhất — khóa idempotency của callback IPN (BR-PAY-03). */
    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;

    /** Giỏ hàng (06/09/2026, mở rộng ngoài đặc tả gốc) — các Payment tạo CÙNG 1 lần bấm
     * "Proceed to Checkout" (gộp thanh toán nhiều khóa) chia sẻ chung giá trị này, để
     * webhook cổng thanh toán xác nhận CẢ NHÓM cùng lúc (xem {@code PaymentService.
     * processGatewayCallback}). NULL ở luồng mua 1 khóa trực tiếp (UC13/UC14, không đổi). */
    @Column(name = "order_group_ref", length = 50)
    private String orderGroupRef;

    /** Lấy từ giá server, KHÔNG nhận từ client (BR-PAY-02). Là giá THỰC THU, đã trừ giảm giá
     * nếu có coupon áp dụng (15/09/2026, mở rộng) — BR-PAY-05 (30/70) tính trên field này. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Giá khóa học TRƯỚC khi trừ coupon — NULL nếu không áp coupon nào (15/09/2026, mở rộng). */
    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    /** = originalAmount - amount, mặc định 0 nếu không có coupon (15/09/2026, mở rộng). */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Coupon đã áp dụng cho giao dịch này — NULL nếu không dùng coupon (15/09/2026, mở rộng). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    /** = amount x 30%, chốt cứng lúc PAID (BR-PAY-05). */
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee = BigDecimal.ZERO;

    /** = amount x (63% hoặc 3% tùy {@link #revenueSource}), chốt cứng lúc PAID (BR-PAY-05). */
    @Column(name = "instructor_earning", nullable = false, precision = 12, scale = 2)
    private BigDecimal instructorEarning = BigDecimal.ZERO;

    /** Chốt lúc TẠO đơn (không đổi được sau đó) — quyết định tỷ lệ ăn chia áp dụng lúc PAID,
     * xem docblock lớp. */
    @Enumerated(EnumType.STRING)
    @Column(name = "revenue_source", nullable = false, length = 20)
    private RevenueSource revenueSource = RevenueSource.ORGANIC;

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
