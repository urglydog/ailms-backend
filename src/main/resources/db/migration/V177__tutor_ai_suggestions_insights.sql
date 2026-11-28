-- ---------------------------------------------------------------------
-- TÍNH NĂNG MỞ RỘNG (26/11/2026) — Gia sư AI
-- lesson_ai_suggestions: cache câu hỏi gợi ý do AI sinh theo bài (nguồn AI của
--   GET /lessons/{id}/tutor-suggestions). fingerprint = dấu vân tay nội dung bài
--   (tiêu đề/mô tả/chương/transcript) — nội dung đổi thì fingerprint lệch -> sinh lại.
--   Job nền (@Scheduled) xử lý các dòng PENDING; request KHÔNG BAO GIỜ chờ LLM.
-- course_tutor_insights: tóm tắt + đề xuất do AI viết cho giảng viên (Insight AI), sinh
--   hằng ngày hoặc theo yêu cầu, chỉ từ số liệu ĐÃ GỘP (không câu hỏi nguyên văn).
-- ---------------------------------------------------------------------
CREATE TABLE lesson_ai_suggestions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    questions TEXT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lesson_ai_suggestions_lesson UNIQUE (lesson_id),
    CONSTRAINT fk_lesson_ai_suggestions_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_lesson_ai_suggestions_status ON lesson_ai_suggestions (status);

CREATE TABLE course_tutor_insights (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary TEXT NULL,
    actions TEXT NULL,
    generated_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_tutor_insights_course UNIQUE (course_id),
    CONSTRAINT fk_course_tutor_insights_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_course_tutor_insights_status ON course_tutor_insights (status);
