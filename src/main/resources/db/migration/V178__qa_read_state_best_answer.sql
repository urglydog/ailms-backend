-- ---------------------------------------------------------------------
-- Hỏi đáp (27/11/2026): trạng thái "đã đọc" của giảng viên + "câu trả lời hay nhất".
-- Mỗi khoá chỉ có 1 giảng viên sở hữu nên lưu thẳng trên câu hỏi (tin GỐC của lesson_chats):
--   instructor_read_at: lúc giảng viên mở câu hỏi; NULL = "Chưa đọc" (đặt lại NULL khi có
--     trả lời mới từ người khác giảng viên).
--   best_answer_id: tin trả lời được người hỏi hoặc giảng viên đánh dấu là hay nhất.
-- ---------------------------------------------------------------------
ALTER TABLE lesson_chats ADD COLUMN instructor_read_at DATETIME(6) NULL;
ALTER TABLE lesson_chats ADD COLUMN best_answer_id VARCHAR(36) NULL;
ALTER TABLE lesson_chats ADD CONSTRAINT fk_lesson_chats_best_answer
    FOREIGN KEY (best_answer_id) REFERENCES lesson_chats (id) ON DELETE SET NULL;
