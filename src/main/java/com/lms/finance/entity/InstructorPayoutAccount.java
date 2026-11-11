package com.lms.finance.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.NameMatchStatus;
import com.lms.common.enums.PayoutAccountStatus;
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

/** Tài khoản ngân hàng nhận tiền của giảng viên (V161). Số tài khoản chỉ lưu dạng mã hóa — xem {@code PayoutCrypto}. */
@Entity
@Table(name = "instructor_payout_accounts")
@Getter
@Setter
public class InstructorPayoutAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_bin", nullable = false, length = 10)
    private String bankBin;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** AES-GCM (định dạng `v1:base64(iv+ciphertext)`); KHÔNG bao giờ ghi ra log hoặc trả về API giảng viên. */
    @Column(name = "account_number_enc", nullable = false, length = 255)
    private String accountNumberEnc;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    /** HMAC-SHA256 của số tài khoản — dò trùng giữa nhiều giảng viên, không đảo ngược được. */
    @Column(name = "account_hash", nullable = false, length = 64)
    private String accountHash;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutAccountStatus status = PayoutAccountStatus.PENDING_CONFIRM;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_match_status", nullable = false, length = 20)
    private NameMatchStatus nameMatchStatus = NameMatchStatus.MISMATCH;

    /** Chỉ dùng để rút tiền từ thời điểm này (đổi tài khoản → khóa vài ngày chống chiếm tài khoản). */
    @Column(name = "usable_from")
    private LocalDateTime usableFrom;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "replaced_at")
    private LocalDateTime replacedAt;
}
