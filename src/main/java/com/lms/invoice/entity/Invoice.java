package com.lms.invoice.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
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

/**
 * Chứng từ tài chính (20/11/2026). BẤT BIẾN: sau khi phát hành chỉ {@code status} được chuyển sang VOIDED (khi có phiếu điều chỉnh);
 * nội dung hiển thị luôn lấy từ {@link #snapshotJson}, không tính lại từ dữ liệu hiện tại.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(name = "number", nullable = false, unique = true, length = 30, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private InvoiceType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_user_id", nullable = false, updatable = false)
    private User party;

    /** Khóa nguồn giao dịch: mã nhóm đơn/mã đơn (RECEIPT), id khoản hoàn (REFUND), id yêu cầu rút (PAYOUT), id lần trả nợ (DEBT_RECEIPT). */
    @Column(name = "source_key", nullable = false, length = 100, updatable = false)
    private String sourceKey;

    /** Id số của nguồn cho 3 loại REFUND/PAYOUT/DEBT_RECEIPT (NULL với RECEIPT); dùng để so khớp bằng số trong truy vấn. */
    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_invoice_id", updatable = false)
    private Invoice relatedInvoice;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String snapshotJson;

    /**
     * Mã truy cập ngẫu nhiên (256 bit, base64url 43 ký tự) trong liên kết/QR in trên PDF để mở thẳng file. Là "chìa khóa" của liên kết nên không bao giờ
     * trả ra trong API danh sách/chi tiết; cấp lúc phát hành (chứng từ cũ được cấp bằng job bù một lần).
     */
    @Column(name = "access_token", length = 64, unique = true)
    private String accessToken;

    @Column(name = "template_version", nullable = false, updatable = false)
    private Integer templateVersion = 1;
}
