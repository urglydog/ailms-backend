package com.lms.invoice.service;

import com.lms.common.enums.DebtPaymentStatus;
import com.lms.common.enums.PayoutStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RefundTransferStatus;
import com.lms.common.service.SystemConfigService;
import com.lms.finance.entity.InstructorDebtPayment;
import com.lms.finance.entity.InstructorPayout;
import com.lms.invoice.entity.InvoiceType;
import com.lms.payment.entity.Payment;
import com.lms.payment.entity.PaymentRefund;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tìm các giao dịch ĐÃ HOÀN TẤT nhưng CHƯA có chứng từ — nguồn việc cho {@link InvoiceIssueJob} (phát hành mới và phát bù/backfill dùng chung).
 * Tài khoản demo bị loại ngay trong truy vấn (đuôi email cấu hình {@code INVOICE_EXCLUDED_EMAIL_SUFFIX}, mã đơn bắt đầu {@code DEMO-}),
 * nếu không job sẽ quét đi quét lại đúng những dòng đó mỗi lần chạy.
 */
@Component
public class InvoiceSourceFinder {

    static final String CFG_EXCLUDED_SUFFIX = "INVOICE_EXCLUDED_EMAIL_SUFFIX";
    static final String DEFAULT_EXCLUDED_SUFFIX = "@example.com";
    /** Mẫu LIKE không bao giờ khớp email thật — dùng khi cấu hình để trống (không loại trừ ai). */
    private static final String MATCH_NOTHING = "%\u0001";

    @PersistenceContext
    private EntityManager em;

    private final SystemConfigService config;

    public InvoiceSourceFinder(SystemConfigService config) {
        this.config = config;
    }

    /** Mẫu LIKE (chữ thường) của email bị loại trừ. */
    String excludedEmailPattern() {
        String suffix = config.getString(CFG_EXCLUDED_SUFFIX, DEFAULT_EXCLUDED_SUFFIX);
        if (suffix == null || suffix.isBlank()) {
            return MATCH_NOTHING;
        }
        return "%" + suffix.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsWithoutReceipt(int limit) {
        return em.createQuery("SELECT p FROM Payment p JOIN FETCH p.user u JOIN FETCH p.course WHERE p.status IN :statuses "
                        + "AND p.amount > 0 AND p.paidAt IS NOT NULL AND p.txnRef NOT LIKE 'DEMO-%' AND LOWER(u.email) NOT LIKE :excluded "
                        + "AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.type = :type AND i.sourceKey = COALESCE(p.orderGroupRef, p.txnRef)) "
                        + "ORDER BY p.id", Payment.class)
                .setParameter("statuses", List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))
                .setParameter("excluded", excludedEmailPattern())
                .setParameter("type", InvoiceType.RECEIPT)
                .setMaxResults(limit).getResultList();
    }

    @Transactional(readOnly = true)
    public List<PaymentRefund> refundsWithoutVoucher(int limit) {
        return em.createQuery("SELECT r FROM PaymentRefund r JOIN FETCH r.payment p JOIN FETCH p.user u JOIN FETCH p.course "
                        + "WHERE r.transferStatus = :transferred AND r.amount > 0 AND r.transferredAt IS NOT NULL "
                        + "AND p.txnRef NOT LIKE 'DEMO-%' AND LOWER(u.email) NOT LIKE :excluded "
                        + "AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.type = :type AND i.sourceId = r.id) "
                        + "ORDER BY r.id", PaymentRefund.class)
                .setParameter("transferred", RefundTransferStatus.TRANSFERRED)
                .setParameter("excluded", excludedEmailPattern())
                .setParameter("type", InvoiceType.REFUND)
                .setMaxResults(limit).getResultList();
    }

    @Transactional(readOnly = true)
    public List<InstructorPayout> payoutsWithoutVoucher(int limit) {
        return em.createQuery("SELECT o FROM InstructorPayout o JOIN FETCH o.instructor u WHERE o.status = :paid AND o.amount > 0 "
                        + "AND LOWER(u.email) NOT LIKE :excluded "
                        + "AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.type = :type AND i.sourceId = o.id) "
                        + "ORDER BY o.id", InstructorPayout.class)
                .setParameter("paid", PayoutStatus.PAID)
                .setParameter("excluded", excludedEmailPattern())
                .setParameter("type", InvoiceType.PAYOUT)
                .setMaxResults(limit).getResultList();
    }

    @Transactional(readOnly = true)
    public List<InstructorDebtPayment> debtPaymentsWithoutReceipt(int limit) {
        return em.createQuery("SELECT d FROM InstructorDebtPayment d JOIN FETCH d.instructor u WHERE d.status = :paid AND d.amount > 0 "
                        + "AND LOWER(u.email) NOT LIKE :excluded "
                        + "AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.type = :type AND i.sourceId = d.id) "
                        + "ORDER BY d.id", InstructorDebtPayment.class)
                .setParameter("paid", DebtPaymentStatus.PAID)
                .setParameter("excluded", excludedEmailPattern())
                .setParameter("type", InvoiceType.DEBT_RECEIPT)
                .setMaxResults(limit).getResultList();
    }
}
