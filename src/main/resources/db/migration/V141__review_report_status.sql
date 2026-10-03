-- AI Sentiment Filter (UpComming_Plan.md Sprint 2 mục 6, 03/10/2026) — Refined AC "Giảng viên
-- có quyền Report 1 review, review đó bị ẩn tạm thời và chờ Admin duyệt tay" chưa được code dù
-- mục này đã bị đánh dấu ĐÃ HOÀN THÀNH. Thêm trạng thái PENDING_REPORT phân biệt với HIDDEN
-- (ẩn do AI/Admin) để Admin có hàng chờ riêng cần xử lý.
ALTER TABLE course_reviews
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE';

UPDATE course_reviews SET moderation_status = 'HIDDEN' WHERE is_hidden = true;
