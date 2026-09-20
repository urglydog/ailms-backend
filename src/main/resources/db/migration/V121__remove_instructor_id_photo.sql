-- (20/09/2026, theo yêu cầu) — bỏ hẳn bước upload ảnh CCCD khi xác minh định danh Giảng viên:
-- form chỉ còn số CCCD/CMND + địa chỉ + xác nhận sở hữu nội dung. Ảnh đã upload trước đó (nếu
-- có) được xoá thủ công khỏi Backblaze B2 trước khi chạy migration này (xem lịch sử trao đổi).
ALTER TABLE instructor_verifications DROP COLUMN id_photo_url;
