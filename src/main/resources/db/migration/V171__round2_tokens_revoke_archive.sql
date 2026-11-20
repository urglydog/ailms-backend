-- Đợt chỉnh 2 sau nghiệm thu hóa đơn (20/11/2026). Chỉ THÊM cột/chỉ mục/khóa cấu hình; không xóa dữ liệu nào.
-- 1) Chứng từ: mã truy cập ngẫu nhiên cho liên kết/QR mở thẳng PDF (sinh bằng SecureRandom trong ứng dụng, chứng từ cũ được cấp mã bằng job một lần).
ALTER TABLE invoices ADD COLUMN access_token VARCHAR(64) NULL;
ALTER TABLE invoices ADD UNIQUE KEY uk_invoice_access_token (access_token);

-- 2) Thông báo hệ thống: sửa/thu hồi. notifications.source_ref nối từng thông báo của người dùng về thông báo gốc (ANN:<id>); hidden_at = đã thu hồi (ẩn,
--    không xóa). system_announcements.revoked_at = thời điểm thu hồi, bản ghi vẫn còn ở admin.
ALTER TABLE notifications ADD COLUMN source_ref VARCHAR(40) NULL;
ALTER TABLE notifications ADD COLUMN hidden_at DATETIME NULL;
ALTER TABLE notifications ADD INDEX idx_notifications_source_ref (source_ref);
ALTER TABLE system_announcements ADD COLUMN revoked_at DATETIME NULL;
-- Nối các thông báo đã gửi trước đây về thông báo gốc (khớp loại + tiêu đề + nội dung); chỉ điền cột mới, không đổi dữ liệu cũ.
UPDATE notifications n JOIN system_announcements a
    ON n.title = a.title AND n.content = a.content AND n.type = CONCAT('SYSTEM_', a.severity)
SET n.source_ref = CONCAT('ANN:', a.id)
WHERE n.source_ref IS NULL;

-- 3) Gói khóa học: lưu trữ (ngừng bán, giữ nguyên lịch sử đơn) khi đã có người mua.
ALTER TABLE course_bundles ADD COLUMN archived_at DATETIME NULL;

INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('PUBLIC_INVOICE_LINK_ENABLED', '1', 'Cho phép mở chứng từ bằng liên kết/QR trên file PDF (1 = bật, 0 = tắt khẩn cấp)')
ON DUPLICATE KEY UPDATE config_key = config_key;
