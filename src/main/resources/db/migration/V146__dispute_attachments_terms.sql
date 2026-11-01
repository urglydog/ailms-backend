ALTER TABLE payment_disputes
  ADD COLUMN terms_accepted_at DATETIME NULL;

UPDATE payment_disputes SET terms_accepted_at = created_at WHERE terms_accepted_at IS NULL;

ALTER TABLE payment_disputes
  MODIFY COLUMN terms_accepted_at DATETIME NOT NULL;

CREATE TABLE dispute_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispute_id BIGINT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_attachment_dispute FOREIGN KEY (dispute_id) REFERENCES payment_disputes(id)
);
