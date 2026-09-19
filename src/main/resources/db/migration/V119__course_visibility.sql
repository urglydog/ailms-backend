-- (19/09/2026) — "Đăng ký (Quyền riêng tư)" kiểu Udemy: Công khai / Riêng tư (chỉ người được
-- mời) / Riêng tư (mật khẩu). Mặc định PUBLIC để không đổi hành vi của mọi khóa học đã có.
ALTER TABLE courses ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE courses ADD COLUMN enroll_password_hash VARCHAR(255) NULL;

CREATE TABLE IF NOT EXISTS course_invites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_invites_course_email UNIQUE (course_id, email),
    CONSTRAINT fk_course_invites_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
