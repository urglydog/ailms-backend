-- Phase 3: công nợ giảng viên & trả nợ qua PayOS (14/11/2026).
-- Nợ = số dư khả dụng ÂM trong sổ cái (hoàn tiền muộn/chế độ "giảng viên chịu toàn bộ"); doanh thu mới tự bù trừ khi chín.
-- instructor_debt_state: chỉ có dòng khi đang nợ; debt_since để tính bậc thang 30/90 ngày; last_level để chỉ thông báo khi tăng bậc.
-- instructor_debt_payments: mỗi lần giảng viên trả nợ (trả một phần được) qua link/QR PayOS; order_code nằm ở dải >= 1e12 để không bao giờ
-- trùng với orderCode của đơn mua khóa (luôn < 1e9).
CREATE TABLE instructor_debt_state (
    instructor_id BIGINT PRIMARY KEY,
    debt_since DATETIME NOT NULL,
    last_level INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_ids_user FOREIGN KEY (instructor_id) REFERENCES users(id)
);

CREATE TABLE instructor_debt_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instructor_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    order_code BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    checkout_url VARCHAR(500) NULL,
    qr_code TEXT NULL,
    payment_link_id VARCHAR(64) NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_idp_order_code (order_code),
    INDEX idx_idp_instructor (instructor_id, status),
    CONSTRAINT fk_idp_user FOREIGN KEY (instructor_id) REFERENCES users(id)
);

-- Mỗi lần trả nợ chỉ được ghi vào sổ cái 1 lần (webhook gửi trùng/đối soát chạy trùng không cộng 2 lần).
ALTER TABLE instructor_ledger_entries ADD COLUMN debt_payment_id BIGINT NULL;
ALTER TABLE instructor_ledger_entries ADD UNIQUE KEY uk_ile_debt_payment (debt_payment_id);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('DEBT_RESTRICT_DAYS', '30', 'Nợ quá số ngày này: không xuất bản khóa mới, không đăng ký quảng bá'),
  ('DEBT_SUSPEND_DAYS', '90', 'Nợ quá số ngày này: các khóa tạm ngừng bán mới (học viên cũ vẫn học bình thường)'),
  ('DEBT_MIN_REPAY', '10000', 'Số tiền trả nợ tối thiểu mỗi lần (VND), trừ khi trả hết phần còn lại')
ON DUPLICATE KEY UPDATE config_key = config_key;
