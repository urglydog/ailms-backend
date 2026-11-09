-- Restore standard foreign keys if they don't exist
ALTER TABLE material_generations ADD COLUMN chapter_id BIGINT;

-- 2. Create course_resources table for Static Files (PDF, PPT, ZIP, etc)
CREATE TABLE course_resources (
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
