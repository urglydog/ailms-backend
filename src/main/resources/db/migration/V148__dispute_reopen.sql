ALTER TABLE payment_disputes
    ADD COLUMN reopen_reason TEXT NULL,
    ADD COLUMN reopened_at DATETIME NULL,
    ADD COLUMN previous_admin_note TEXT NULL;
