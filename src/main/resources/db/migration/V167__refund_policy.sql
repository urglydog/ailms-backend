-- Phase 4: chính sách hoàn tiền (14/11/2026) — mã lý do chuẩn + minh chứng cho quyết định hoàn tiền, và khiếu nại (appeal) của giảng viên.
-- appeal_status: NONE / OPEN / UPHELD (giữ nguyên) / REDUCED (giảm về chế độ Chuẩn, hoàn lại phần phạt).
ALTER TABLE payment_refunds
    ADD COLUMN reason_code VARCHAR(30) NULL,
    ADD COLUMN evidence_note TEXT NULL,
    ADD COLUMN appeal_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN appeal_reason TEXT NULL,
    ADD COLUMN appeal_at DATETIME NULL,
    ADD COLUMN appeal_decision_note TEXT NULL,
    ADD COLUMN appeal_decided_at DATETIME NULL;

-- Đã cảnh báo admin về tỷ lệ hoàn tiền cao của khóa nào và lúc nào (tránh nhắc lặp mỗi đêm).
CREATE TABLE refund_rate_alerts (
    course_id BIGINT PRIMARY KEY,
    alerted_at DATETIME NOT NULL
);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('DISPUTE_APPEAL_DAYS', '7', 'Số ngày giảng viên được khiếu nại quyết định "giảng viên chịu toàn bộ" kể từ lúc duyệt hoàn tiền'),
  ('REFUND_FLAG_RATE_PCT', '20', 'Cảnh báo admin khi tỷ lệ hoàn tiền của một khóa trong 30 ngày đạt mức % này'),
  ('REFUND_FLAG_MIN_COUNT', '3', 'Số lượt hoàn tiền tối thiểu trong 30 ngày để cảnh báo tỷ lệ cao')
ON DUPLICATE KEY UPDATE config_key = config_key;
