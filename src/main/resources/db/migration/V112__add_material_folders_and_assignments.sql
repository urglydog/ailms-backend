-- 1. Bảng Material Folders
CREATE TABLE IF NOT EXISTS material_folders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    course_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES material_folders(id) ON DELETE SET NULL
);

-- 2. Cập nhật Material Generations (Master File)
ALTER TABLE material_generations ADD COLUMN folder_id BIGINT NULL;
ALTER TABLE material_generations ADD CONSTRAINT fk_mat_folder FOREIGN KEY (folder_id) REFERENCES material_folders(id) ON DELETE SET NULL;
ALTER TABLE material_generations ADD COLUMN is_archived BOOLEAN DEFAULT FALSE;
ALTER TABLE material_generations ADD COLUMN parent_generation_id BIGINT NULL;
ALTER TABLE material_generations ADD CONSTRAINT fk_mat_parent FOREIGN KEY (parent_generation_id) REFERENCES material_generations(id) ON DELETE SET NULL;

-- 3. Bảng Material Assignments (Đa cấp độ: Khóa / Chương / Bài)
CREATE TABLE IF NOT EXISTS material_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    chapter_id BIGINT NULL,
    lesson_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (material_id) REFERENCES material_generations(id) ON DELETE CASCADE
);

-- 4. Data Migration: Di chuyển dữ liệu phân phối cũ sang bảng mới
INSERT INTO material_assignments (material_id, lesson_id, chapter_id)
SELECT id, lesson_id, chapter_id FROM material_generations WHERE lesson_id IS NOT NULL OR chapter_id IS NOT NULL;
