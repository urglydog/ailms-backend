-- Chi tự động qua PayOS (14/11/2026, lõi). Mỗi lệnh chi gửi sang PayOS là 1 dòng ở đây: reference_id (cũng là khóa idempotency gửi PayOS)
-- là duy nhất nên gửi lại cùng lệnh không chi 2 lần; KHÔNG lưu số tài khoản đầy đủ (chỉ 4 số cuối) — số đầy đủ lấy từ bản mã hóa của
-- yêu cầu rút / hồ sơ hoàn tiền ngay lúc gửi. Công tắc PAYOS_PAYOUT_ENABLED mặc định 0 (tắt): bật sau khi kênh chi hộ PayOS sẵn sàng.
CREATE TABLE payos_payout_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    attempt INT NOT NULL DEFAULT 1,
    reference_id VARCHAR(40) NOT NULL,
    amount BIGINT NOT NULL,
    bank_bin VARCHAR(10) NOT NULL,
    account_last4 VARCHAR(4) NOT NULL,
    holder_name_entered VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    payos_payout_id VARCHAR(64) NULL,
    payos_state VARCHAR(30) NULL,
    resolved_account_name VARCHAR(150) NULL,
    bank_reference VARCHAR(100) NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(500) NULL,
    created_by_user_id BIGINT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_ppo_reference (reference_id),
    INDEX idx_ppo_entity (kind, entity_id),
    INDEX idx_ppo_status (status)
);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('PAYOS_PAYOUT_ENABLED', '0', 'Bật chi tự động qua PayOS (1 = bật, 0 = tắt, chỉ chuyển khoản thủ công)'),
  ('PAYOS_PAYOUT_MAX_AMOUNT', '5000000', 'Số tiền tối đa (VND) cho MỘT lệnh chi tự động; lớn hơn thì phải chuyển thủ công')
ON DUPLICATE KEY UPDATE config_key = config_key;
