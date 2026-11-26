-- ---------------------------------------------------------------------
-- lesson_notes (entity: LessonNote, module: note) — TÍNH NĂNG MỞ RỘNG (26/11/2026), ghi chú
-- cá nhân của học viên gắn với 1 mốc thời gian trong video bài học. Chỉ chủ ghi chú đọc/sửa/xoá.
-- course_id lưu trùng (denormalize) để lọc "mọi bài trong khoá" không cần join chapter.
-- ---------------------------------------------------------------------
CREATE TABLE lesson_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    timestamp_sec INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_notes_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_notes_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_notes_course_id FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_lesson_notes_user_course ON lesson_notes (user_id, course_id);
CREATE INDEX idx_lesson_notes_user_lesson ON lesson_notes (user_id, lesson_id);
