-- Giao diện quản lý "Chương trình giảng dạy" kiểu Udemy (15/09/2026, mở rộng) — mỗi Phần
-- (chương) và mỗi Bài giảng có thể có mô tả riêng, tách khỏi tiêu đề.
ALTER TABLE chapters ADD COLUMN description TEXT NULL;
ALTER TABLE lessons ADD COLUMN description TEXT NULL;
