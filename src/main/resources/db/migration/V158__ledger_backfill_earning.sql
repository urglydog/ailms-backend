-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn A) — backfill dòng EARNING cho đơn đã PAID.
--
-- Chưa từng có khoản nào được chi cho giảng viên nên toàn bộ instructor_earning hiện có là
-- khoản nền tảng còn nợ. available_at = paid_at + (hạn hoàn + thời gian giữ thêm) lấy từ
-- system_configs (mặc định 7+3 ngày) → đơn cũ gần như đều đã "chín". Idempotent: chạy lại
-- không tạo trùng nhờ NOT EXISTS + UNIQUE (payment_id, entry_type). Đơn 0đ / không có
-- earning không sinh dòng.
INSERT INTO instructor_ledger_entries (instructor_id, entry_type, amount, available_at, payment_id, note)
SELECT c.instructor_id,
       'EARNING',
       p.instructor_earning,
       DATE_ADD(p.paid_at, INTERVAL (
           COALESCE((SELECT CAST(config_value AS UNSIGNED) FROM system_configs WHERE config_key = 'REFUND_WINDOW_DAYS'), 7)
         + COALESCE((SELECT CAST(config_value AS UNSIGNED) FROM system_configs WHERE config_key = 'PAYOUT_HOLD_EXTRA_DAYS'), 3)
       ) DAY),
       p.id,
       'Backfill V158 từ giao dịch đã PAID'
  FROM payments p
  JOIN courses c ON c.id = p.course_id
 WHERE p.status = 'PAID'
   AND p.instructor_earning > 0
   AND p.paid_at IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM instructor_ledger_entries l
        WHERE l.payment_id = p.id AND l.entry_type = 'EARNING');
