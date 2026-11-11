-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn A) — seed cấu hình tài chính cho system_configs.
--
-- SystemConfigService chỉ UPDATE khóa có sẵn (không tự tạo khóa mới) nên khóa phải seed ở đây.
-- Giá trị mặc định = đúng hành vi hiện tại (37/63 và 97/3) → áp dụng migration KHÔNG đổi kết quả
-- tính tiền. Đổi tỷ lệ sau này chỉ ảnh hưởng đơn tạo SAU ĐÓ (tỷ lệ chốt cứng lúc PAID, xem V154).
-- ON DUPLICATE KEY: chạy lại không ghi đè giá trị admin đã sửa.
INSERT INTO system_configs (config_key, config_value, description) VALUES
  ('REVENUE_ORGANIC_INSTRUCTOR_PCT', '37', 'Giảng viên nhận % khi học viên tự tìm thấy khóa (organic), 0-100'),
  ('REVENUE_REFERRAL_INSTRUCTOR_PCT', '97', 'Giảng viên nhận % khi mua qua link giới thiệu của giảng viên, 0-100'),
  ('REFUND_WINDOW_DAYS', '7', 'Hạn hoàn tiền (ngày) tính từ lúc thanh toán thành công'),
  ('REFUND_MAX_PROGRESS_PCT', '20', 'Tiến độ học tối đa (%) để được hoàn tiền tự động đủ điều kiện'),
  ('REFUND_HOLIDAYS', '', 'Ngày lễ gia hạn hạn hoàn tiền, dạng YYYY-MM-DD cách nhau dấu phẩy (tối đa ~20 ngày)'),
  ('PAYOUT_HOLD_EXTRA_DAYS', '3', 'Số ngày giữ tiền thêm sau hạn hoàn tiền trước khi giảng viên rút được'),
  ('PAYOUT_MIN_AMOUNT', '100000', 'Số tiền rút tối thiểu (VND)'),
  ('PAYOUT_DAYS_OF_MONTH', '5,20', 'Các ngày trong tháng chi trả theo đợt, 1-28 cách nhau dấu phẩy'),
  ('DISPUTE_INSTRUCTOR_RESPONSE_HOURS', '48', 'Số giờ giảng viên được phản hồi khiếu nại/hoàn tiền')
ON DUPLICATE KEY UPDATE config_key = config_key;
