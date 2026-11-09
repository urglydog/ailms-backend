ALTER TABLE material_generations
ADD COLUMN lesson_id BIGINT NULL;

ALTER TABLE material_generations
ADD CONSTRAINT fk_material_generation_lesson
FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE SET NULL;

ALTER TABLE quizzes
ADD COLUMN quiz_type VARCHAR(20) NOT NULL DEFAULT 'OFFICIAL_EXAM';
