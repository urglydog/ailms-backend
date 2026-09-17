-- 1. Tạo bảng course_resources cho tài liệu tĩnh (PDF, Slide, ZIP) nếu chưa có
CREATE TABLE IF NOT EXISTS course_resources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    file_size BIGINT NULL,
    file_type VARCHAR(50) NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Thêm cột soft delete cho bảng material_generations
ALTER TABLE material_generations ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE material_generations ADD COLUMN deleted_at TIMESTAMP NULL;

-- 3. Thêm cột soft delete cho bảng quizzes
ALTER TABLE quizzes ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE quizzes ADD COLUMN deleted_at TIMESTAMP NULL;