-- Chính sách khi chi PayOS thất bại (16/11/2026): phân loại lỗi, thời gian chờ + số lần thử lại tối đa, và mốc học viên cập nhật tài khoản nhận hoàn tiền.
ALTER TABLE payos_payout_orders
    ADD COLUMN failure_category VARCHAR(20) NULL;

ALTER TABLE payment_disputes
    ADD COLUMN refund_account_updated_at DATETIME NULL;

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('PAYOS_RETRY_COOLDOWN_MINUTES', '30', 'Số phút phải chờ giữa 2 lần chi PayOS cho cùng một khoản sau khi thất bại'),
  ('PAYOS_MAX_ATTEMPTS', '3', 'Số lần chi PayOS thất bại tối đa cho một khoản; quá số này thì khóa nút PayOS, chỉ còn chuyển thủ công')
ON DUPLICATE KEY UPDATE config_key = config_key;
