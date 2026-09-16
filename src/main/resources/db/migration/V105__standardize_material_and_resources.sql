-- 1. Revert material_generations table
ALTER TABLE material_generations DROP COLUMN IF EXISTS attached_chapter_id;
ALTER TABLE material_generations DROP COLUMN IF EXISTS attached_lesson_ids;
ALTER TABLE material_generations DROP COLUMN IF EXISTS static_file_url;

-- Restore standard foreign keys if they don't exist
ALTER TABLE material_generations ADD COLUMN IF NOT EXISTS lesson_id BIGINT REFERENCES lessons(id) ON DELETE SET NULL;
ALTER TABLE material_generations ADD COLUMN IF NOT EXISTS chapter_id BIGINT REFERENCES chapters(id) ON DELETE SET NULL;

-- 2. Create course_resources table for Static Files (PDF, PPT, ZIP, etc)
CREATE TABLE IF NOT EXISTS course_resources (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id BIGINT REFERENCES chapters(id) ON DELETE CASCADE,
    lesson_id BIGINT REFERENCES lessons(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_resources_course_id ON course_resources(course_id);
CREATE INDEX idx_course_resources_chapter_id ON course_resources(chapter_id);
CREATE INDEX idx_course_resources_lesson_id ON course_resources(lesson_id);
