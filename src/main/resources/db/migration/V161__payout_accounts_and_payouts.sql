-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C) — tài khoản nhận tiền và yêu cầu rút tiền của giảng viên.
-- CHỈ THÊM bảng mới, không đụng bảng cũ.
--
-- instructor_payout_accounts: số tài khoản KHÔNG lưu dạng rõ — account_number_enc là AES-GCM (khóa ở biến môi
-- trường, xem PayoutCrypto), chỉ hiển thị 4 số cuối. account_hash (HMAC) để phát hiện cùng 1 số tài khoản được
-- nhiều giảng viên liên kết. Mỗi giảng viên tối đa 1 tài khoản ACTIVE; tài khoản mới ở PENDING_CONFIRM cho tới khi
-- bấm link xác nhận trong email; đổi tài khoản thì tài khoản mới chỉ dùng được sau usable_from (khóa rút vài ngày).
--
-- instructor_payouts: mỗi yêu cầu rút chụp lại (snapshot) tài khoản tại thời điểm yêu cầu nên đổi/xóa tài khoản
-- sau đó không làm sai lệch yêu cầu đang xử lý. UNIQUE (instructor_id, idempotency_key) chống bấm 2 lần.
CREATE TABLE instructor_payout_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bank_code VARCHAR(20) NOT NULL,
    bank_bin VARCHAR(10) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_number_enc VARCHAR(255) NOT NULL,
    key_version INT NOT NULL DEFAULT 1,
    account_last4 VARCHAR(4) NOT NULL,
    account_hash VARCHAR(64) NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    name_match_status VARCHAR(20) NOT NULL,
    usable_from DATETIME NULL,
    confirmed_at DATETIME NULL,
    replaced_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_ipa_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_ipa_user_status (user_id, status),
    INDEX idx_ipa_hash (account_hash)
);

CREATE TABLE instructor_payouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    instructor_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    account_id BIGINT NULL,
    bank_code VARCHAR(20) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_number_enc VARCHAR(255) NOT NULL,
    account_last4 VARCHAR(4) NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(64) NULL,
    scheduled_for DATE NULL,
    email_confirmed_at DATETIME NULL,
    approved_by_user_id BIGINT NULL,
    approved_at DATETIME NULL,
    paid_by_user_id BIGINT NULL,
    paid_at DATETIME NULL,
    transfer_ref VARCHAR(100) NULL,
    proof_url VARCHAR(500) NULL,
    reject_reason VARCHAR(500) NULL,
    admin_note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_ip_instructor FOREIGN KEY (instructor_id) REFERENCES users(id),
    CONSTRAINT fk_ip_account FOREIGN KEY (account_id) REFERENCES instructor_payout_accounts(id),
    CONSTRAINT fk_ip_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_ip_payer FOREIGN KEY (paid_by_user_id) REFERENCES users(id),
    UNIQUE KEY uk_ip_code (code),
    UNIQUE KEY uk_ip_idempotency (instructor_id, idempotency_key),
    INDEX idx_ip_status (status),
    INDEX idx_ip_instructor_status (instructor_id, status)
);
