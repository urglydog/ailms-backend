-- AI Anti-Cheat (Composite Behavioral Risk Engine) + Auto-ban bang AI (25/09/2026)

-- started_at rieng khong can: QuizAttempt.createdAt da la thoi diem bat dau lam bai
-- (duoc StartRes tra ve dung nhu vay tu truoc), dung lai cho offset-giay cua marker vi pham.
ALTER TABLE quiz_attempts
    ADD COLUMN violation_count INT NOT NULL DEFAULT 0,
    ADD COLUMN ai_risk_level VARCHAR(10) NULL,
    ADD COLUMN ai_risk_explanation TEXT NULL;

CREATE TABLE quiz_attempt_violations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    detail TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempt_violations_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    INDEX idx_quiz_attempt_violations_attempt (attempt_id)
) ENGINE = InnoDB;

CREATE TABLE proctoring_recordings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    video_url VARCHAR(1000) NOT NULL,
    duration_sec INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_proctoring_recordings_attempt UNIQUE (attempt_id),
    CONSTRAINT fk_proctoring_recordings_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Auto-ban bang AI (de xuat khoa, khong tu khoa - Admin duyet)
ALTER TABLE users
    ADD COLUMN ai_lock_proposed_at DATETIME NULL,
    ADD COLUMN ai_lock_proposed_reason VARCHAR(500) NULL;
