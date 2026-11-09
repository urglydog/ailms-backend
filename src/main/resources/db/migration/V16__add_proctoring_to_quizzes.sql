-- V16__add_proctoring_to_quizzes.sql
ALTER TABLE quizzes 
ADD COLUMN is_proctored BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN max_violations INT DEFAULT 3;
