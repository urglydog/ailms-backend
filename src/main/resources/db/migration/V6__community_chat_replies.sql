ALTER TABLE lesson_chats ADD COLUMN parent_id VARCHAR(36) NULL;
ALTER TABLE lesson_chats ADD CONSTRAINT fk_lesson_chats_parent FOREIGN KEY (parent_id) REFERENCES lesson_chats(id) ON DELETE CASCADE;
