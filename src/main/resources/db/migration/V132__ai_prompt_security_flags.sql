-- Chong Prompt Injection / DoW qua AI Discovery + Instructor AI Assistant (UpComming_Plan.md,
-- phan tich 25/09/2026) — bang log-only, AI Worker ghi vao khi phat hien pattern nghi van, KHONG
-- chan cau tra loi, chi de Admin xem lai.

CREATE TABLE ai_prompt_security_flags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_email VARCHAR(255) NULL,
    source VARCHAR(30) NOT NULL,
    matched_pattern VARCHAR(100) NOT NULL,
    message_snapshot TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ai_prompt_security_flags_created_at (created_at)
) ENGINE = InnoDB;
