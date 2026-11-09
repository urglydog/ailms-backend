-- F11.9 mở rộng (05/09/2026) — ảnh thumbnail riêng cho buổi live, hiện trên trang khám phá
-- `/live` (giống các nền tảng live hiện có: Nimo TV, Twitch...). NULL nghĩa là giảng viên chưa
-- tự tải ảnh riêng — FE/service tầng đọc TỰ RƠI VỀ `courses.thumbnail_url` của khóa học, không
-- bắt buộc phải có ảnh riêng cho từng buổi (quyết định đã chốt với người dùng: không phiền giảng
-- viên tải thêm ảnh nếu không cần tùy chỉnh).
ALTER TABLE live_sessions
    ADD COLUMN thumbnail_url VARCHAR(500) NULL AFTER title;
