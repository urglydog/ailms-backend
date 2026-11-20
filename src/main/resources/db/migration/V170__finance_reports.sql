-- Thống kê tài chính & báo cáo tự động (20/11/2026), Hóa đơn H2–H4. Chỉ THÊM bảng/khóa cấu hình, không đụng dữ liệu cũ.
-- report_snapshots: bản chụp số liệu của mỗi lần tạo báo cáo (định kỳ hoặc chạy tay). dedupe_key chỉ có ở báo cáo định kỳ
-- (vd DAILY:2026-11-19) và UNIQUE để job chạy lại/bù không tạo trùng; báo cáo chạy tay để NULL.
CREATE TABLE report_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(10) NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    trigger_type VARCHAR(10) NOT NULL,
    dedupe_key VARCHAR(60) NULL,
    created_by_user_id BIGINT NULL,
    summary_json TEXT NOT NULL,
    emailed_to VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    UNIQUE KEY uk_report_dedupe (dedupe_key),
    INDEX idx_report_created (created_at)
);

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('REPORT_DAILY_ENABLED', '0', 'Gửi báo cáo tài chính hằng ngày (1 = bật, 0 = tắt)'),
  ('REPORT_WEEKLY_ENABLED', '1', 'Gửi báo cáo tài chính hằng tuần, vào sáng thứ Hai (1 = bật, 0 = tắt)'),
  ('REPORT_MONTHLY_ENABLED', '1', 'Gửi báo cáo tài chính hằng tháng, vào sáng ngày 1 (1 = bật, 0 = tắt)'),
  ('REPORT_RECIPIENTS', '', 'Email nhận báo cáo, cách nhau bằng dấu phẩy; để trống = tất cả quản trị viên'),
  ('STATEMENT_EMAIL_INSTRUCTORS', '0', 'Gửi bảng kê thu nhập tháng qua email cho từng giảng viên (1 = bật, 0 = chỉ xem trên giao diện)'),
  ('PAYOS_PAYOUT_FEE', '2950', 'Phí PayOS cho mỗi lệnh chi thành công (VND), nền tảng chịu; dùng để tính chi phí trong thống kê')
ON DUPLICATE KEY UPDATE config_key = config_key;
