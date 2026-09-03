-- ===========================================================================
-- V15: Nâng cấp hệ thống cho Công việc 5 (Quyền hạn Instructor & Admin)
-- ---------------------------------------------------------------------------
-- ⚠ Mọi ALTER đều ADD COLUMN với DEFAULT hoặc NULL → an toàn cho dữ liệu cũ.
-- ===========================================================================

-- 1. Nâng cấp bảng quizzes: cấu hình thi do Giảng viên quản lý
ALTER TABLE quizzes ADD COLUMN random_pick_count INT DEFAULT NULL
    COMMENT 'So cau boc ngau nhien moi lan thi. NULL = lay het';

ALTER TABLE quizzes ADD COLUMN allow_review TINYINT(1) NOT NULL DEFAULT 1
    COMMENT 'Cho phep hoc vien xem lai dap an sau khi nop bai';

ALTER TABLE quizzes ADD COLUMN start_time DATETIME DEFAULT NULL
    COMMENT 'Thoi diem mo de thi. NULL = mo ngay';

ALTER TABLE quizzes ADD COLUMN end_time DATETIME DEFAULT NULL
    COMMENT 'Thoi diem dong de thi (Deadline). NULL = khong gioi han';

ALTER TABLE quizzes ADD COLUMN duration_minutes INT DEFAULT NULL
    COMMENT 'Thoi gian lam bai (phut). NULL = khong gioi han';

ALTER TABLE quizzes ADD COLUMN max_attempts INT DEFAULT NULL
    COMMENT 'So lan lam bai toi da. NULL = khong gioi han (Practice mode)';

-- 2. Thêm cờ is_official cho Mindmap và FlashcardDeck
ALTER TABLE mindmaps ADD COLUMN is_official TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Hoc lieu chuan do Giang vien danh dau';

ALTER TABLE flashcard_decks ADD COLUMN is_official TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Hoc lieu chuan do Giang vien danh dau';

-- 3. Thêm cờ khóa quyền AI cho User
ALTER TABLE users ADD COLUMN is_ai_locked TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Admin khoa quyen goi AI cua user nay';

-- 4. Bảng mới: Lưu vết tiêu thụ Token/Chi phí LLM cho Admin tracking
CREATE TABLE ai_usage_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    feature_type    VARCHAR(50)  NOT NULL COMMENT 'QUIZ, FLASHCARD, MINDMAP, TUTOR, DISCOVERY, DUBBING',
    prompt_tokens   INT          NOT NULL DEFAULT 0,
    completion_tokens INT        NOT NULL DEFAULT 0,
    total_tokens    INT          NOT NULL DEFAULT 0,
    cost_usd        DECIMAL(10,6) NOT NULL DEFAULT 0.000000,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     DEFAULT NULL,
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_ai_usage_user (user_id),
    INDEX idx_ai_usage_feature (feature_type),
    INDEX idx_ai_usage_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
