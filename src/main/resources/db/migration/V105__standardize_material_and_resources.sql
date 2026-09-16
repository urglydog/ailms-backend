-- 1. Revert material_generations table
ALTER TABLE material_generations DROP COLUMN IF EXISTS attached_chapter_id;
ALTER TABLE material_generations DROP COLUMN IF EXISTS attached_lesson_ids;
ALTER TABLE material_generations DROP COLUMN IF EXISTS static_file_url;

-- Restore standard foreign keys if they don't exist
-- In MySQL, ADD COLUMN IF NOT EXISTS requires MariaDB or MySQL 8.0.16+ which we are on, but for FK we need standard syntax
ALTER TABLE material_generations ADD COLUMN IF NOT EXISTS lesson_id BIGINT;
ALTER TABLE material_generations ADD COLUMN IF NOT EXISTS chapter_id BIGINT;

-- 2. Create course_resources table for Static Files (PDF, PPT, ZIP, etc)
CREATE TABLE IF NOT EXISTS course_resources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT,
    lesson_id BIGINT,
    title VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

CREATE INDEX idx_course_resources_course_id ON course_resources(course_id);
CREATE INDEX idx_course_resources_chapter_id ON course_resources(chapter_id);
CREATE INDEX idx_course_resources_lesson_id ON course_resources(lesson_id);
