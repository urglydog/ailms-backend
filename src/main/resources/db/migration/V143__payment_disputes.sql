CREATE TABLE payment_disputes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    filed_by_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    refund_bank_account VARCHAR(100),
    admin_note TEXT,
    resolved_by_user_id BIGINT,
    resolved_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_dispute_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_dispute_filer FOREIGN KEY (filed_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_dispute_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users(id),
    INDEX idx_dispute_status (status),
    INDEX idx_dispute_payment (payment_id)
);
