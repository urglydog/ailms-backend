-- (20/09/2026, tính năng mới) — Chia doanh thu 2 mức thay cho tỷ lệ cố định 30/70 (BR-PAY-05 cũ):
--   * ORGANIC (học viên tự tìm thấy khóa học trên nền tảng): Giảng viên 37% / nền tảng 63%.
--   * INSTRUCTOR_REFERRAL (mua qua liên kết giới thiệu riêng của Giảng viên): Giảng viên 97% / nền tảng 3%.
--
-- `courses.referral_code`: mã ngẫu nhiên sinh 1 LẦN cho mỗi khóa, dùng để dựng liên kết giới
-- thiệu `/courses/{slug}?ref={code}`. Cột thêm NULL trước, backfill bằng UUID() (MySQL sinh
-- giá trị RIÊNG cho từng dòng trong 1 câu UPDATE, không phải 1 giá trị dùng chung), rồi mới
-- khoá NOT NULL + UNIQUE.
ALTER TABLE courses ADD COLUMN referral_code VARCHAR(20) NULL;
UPDATE courses SET referral_code = SUBSTRING(REPLACE(UUID(), '-', ''), 1, 10) WHERE referral_code IS NULL;
ALTER TABLE courses MODIFY COLUMN referral_code VARCHAR(20) NOT NULL;
ALTER TABLE courses ADD CONSTRAINT uk_courses_referral_code UNIQUE (referral_code);

-- `payments.revenue_source`: chốt lúc TẠO đơn, quyết định tỷ lệ tính lúc PAID (xem
-- PaymentService.resolveRevenueSource/applyOutcome). Dữ liệu cũ mặc định ORGANIC — các
-- payments cũ đã PAID giữ nguyên platform_fee/instructor_earning 30/70 đã chốt trước đây,
-- không tính lại (đúng tinh thần BR-PAY-05: chốt cứng lúc PAID, không tính lại lúc hiển thị).
ALTER TABLE payments ADD COLUMN revenue_source VARCHAR(20) NOT NULL DEFAULT 'ORGANIC';
