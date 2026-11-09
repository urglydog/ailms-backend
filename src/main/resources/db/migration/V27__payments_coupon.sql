-- ---------------------------------------------------------------------
-- Gắn coupon vào giao dịch thanh toán (15/09/2026, mở rộng) — `amount` TIẾP TỤC là giá THỰC
-- THU (đã trừ giảm giá nếu có), để công thức 30/70 (BR-PAY-05) ở PaymentService.applyOutcome
-- không cần sửa gì. `original_amount`/`discount_amount` chỉ phục vụ hiển thị/lịch sử.
-- `coupon_id` dùng ON DELETE SET NULL — xoá 1 coupon không được phép làm vỡ lịch sử giao dịch
-- đã có, giữ nguyên discount_amount/original_amount để tra cứu dù coupon gốc không còn.
-- ---------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN coupon_id BIGINT NULL AFTER course_id;
ALTER TABLE payments ADD COLUMN original_amount DECIMAL(12,2) NULL AFTER amount;
ALTER TABLE payments ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER original_amount;
ALTER TABLE payments ADD CONSTRAINT fk_payments_coupon_id FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE SET NULL;
CREATE INDEX idx_payments_coupon_id ON payments (coupon_id);
