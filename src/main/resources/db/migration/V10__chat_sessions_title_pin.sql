-- UC30 mở rộng — đổi tên thủ công (title, NULL = chưa đặt, dùng câu hỏi đầu/tên AI tự đặt)
-- và ghim cuộc trò chuyện (is_pinned + pinned_at để sắp thứ tự các mục đã ghim, mới ghim
-- nhất lên trên).
ALTER TABLE chat_sessions ADD COLUMN title VARCHAR(255) NULL;
ALTER TABLE chat_sessions ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_sessions ADD COLUMN pinned_at DATETIME NULL;
