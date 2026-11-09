ALTER TABLE quiz_questions
ADD COLUMN topic_tag VARCHAR(100),
ADD COLUMN video_timestamp INT,
ADD COLUMN reference_lesson_id BIGINT;

-- Fallback for legacy data
UPDATE quiz_questions qq
SET 
    topic_tag = 'kiến thức chung',
    reference_lesson_id = (
        SELECT mg.lesson_id 
        FROM quizzes q 
        JOIN material_generations mg ON q.material_generation_id = mg.id
        WHERE q.id = qq.quiz_id
    )
WHERE topic_tag IS NULL OR reference_lesson_id IS NULL;
