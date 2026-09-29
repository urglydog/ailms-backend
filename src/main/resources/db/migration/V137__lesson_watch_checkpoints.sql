-- Sprint 3 mục 10 — Retention Heatmap (Drop-off Rate) cho trang "Hiệu suất" Giảng viên.
-- Mỗi dòng: 1 học viên đã xem TỚI (ít nhất) mốc decile này của 1 bài học, ghi 1 lần duy nhất
-- (UNIQUE) nhờ INSERT IGNORE ở tầng Repository — không cần đọc trước khi ghi.
CREATE TABLE lesson_watch_checkpoints (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    decile TINYINT NOT NULL, -- 1..10, ứng với đã xem tới 10%/20%/.../100% thời lượng video
    reached_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_lesson_watch_checkpoint (lesson_id, user_id, decile),
    CONSTRAINT fk_watch_checkpoint_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_checkpoint_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_watch_checkpoint_lesson ON lesson_watch_checkpoints(lesson_id);
