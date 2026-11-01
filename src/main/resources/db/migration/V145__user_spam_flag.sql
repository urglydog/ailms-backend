ALTER TABLE users
  ADD COLUMN is_spam_flagged BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN spam_flag_reason VARCHAR(500) NULL;

CREATE INDEX idx_users_spam_flagged ON users (is_spam_flagged);
