-- V8__add_is_official_to_quizzes.sql
ALTER TABLE quizzes 
ADD COLUMN is_official BOOLEAN NOT NULL DEFAULT FALSE;
