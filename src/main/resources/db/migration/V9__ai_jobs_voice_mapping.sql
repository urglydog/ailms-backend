ALTER TABLE ai_jobs ADD COLUMN voice_mapping_id BIGINT NULL;
ALTER TABLE ai_jobs ADD CONSTRAINT fk_ai_jobs_voice_mapping FOREIGN KEY (voice_mapping_id) REFERENCES voice_mappings (id);
