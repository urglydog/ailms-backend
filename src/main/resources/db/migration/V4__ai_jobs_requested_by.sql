ALTER TABLE ai_jobs ADD COLUMN requested_by_user_id BIGINT NULL;
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users (id);
