-- (19/09/2026, khôi phục) — bảng `instructor_verifications` (tính năng "Xác minh định danh
-- Giảng viên", BR-VERIFY-01) đã bị mất khỏi CSDL sau khi merge PR #132 (`urglydog/feat/
-- additional-features`) dù `flyway_schema_history` vẫn ghi nhận V25 "thành công" — Flyway vì
-- vậy sẽ KHÔNG tự chạy lại V25, phải tạo bảng ở migration MỚI này. Giữ nguyên cấu trúc y hệt V25.
CREATE TABLE IF NOT EXISTS instructor_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    id_number VARCHAR(50) NOT NULL,
    id_photo_url VARCHAR(500) NOT NULL,
    address_text VARCHAR(500) NOT NULL,
    content_ownership_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_instructor_verifications_user UNIQUE (user_id),
    CONSTRAINT fk_instructor_verifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
