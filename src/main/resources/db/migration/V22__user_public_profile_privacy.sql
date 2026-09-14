-- ---------------------------------------------------------------------
-- users.courses_public / users.wishlist_public (entity: User) — TÍNH NĂNG MỞ RỘNG
-- (14/09/2026), không nằm trong 49 use case đặc tả gốc — "View public profile" kiểu Udemy:
-- người khác bấm vào hồ sơ 1 học viên/giảng viên sẽ thấy khóa học đã học và/hoặc wishlist
-- của họ, TRỪ KHI người đó tự tắt riêng từng mục. Mặc định TRUE (công khai) — khớp hành vi
-- mặc định thật của Udemy, người dùng tự tắt nếu muốn ẩn.
-- ---------------------------------------------------------------------
ALTER TABLE users ADD COLUMN courses_public BOOLEAN NOT NULL DEFAULT TRUE AFTER is_ai_locked;
ALTER TABLE users ADD COLUMN wishlist_public BOOLEAN NOT NULL DEFAULT TRUE AFTER courses_public;
