-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn A) — chốt cứng TỶ LỆ nền tảng đã áp dụng cho từng đơn.
--
-- Trước đây tỷ lệ 63%/3% hard-code trong PaymentService.applyOutcome, nên không có cách nào biết
-- một đơn cũ đã dùng tỷ lệ nào ngoài suy từ revenue_source + ngày. Từ nay tỷ lệ là cấu hình admin
-- (system_configs), đổi được; mỗi đơn lưu lại tỷ lệ thực tế đã dùng lúc PAID để báo cáo/hiển thị
-- không bao giờ lệch dù cấu hình đổi sau đó. Chỉ THÊM cột, không xóa/sửa dữ liệu cũ.
ALTER TABLE payments ADD COLUMN platform_rate DECIMAL(5,4) NULL;

-- Backfill từ chính dữ liệu đã chốt (platform_fee / amount) — chính xác hơn suy từ ngày/nguồn.
-- Đơn chưa PAID hoặc 0đ giữ NULL.
UPDATE payments
   SET platform_rate = ROUND(platform_fee / amount, 4)
 WHERE status = 'PAID' AND amount > 0 AND platform_rate IS NULL;
