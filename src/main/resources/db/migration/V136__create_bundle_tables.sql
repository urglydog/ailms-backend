-- 1. Create table course_bundles
CREATE TABLE IF NOT EXISTS course_bundles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instructor_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    discount_percent INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bundle_instructor FOREIGN KEY (instructor_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2. Create join table course_bundle_items
CREATE TABLE IF NOT EXISTS course_bundle_items (
    bundle_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bundle_id, course_id),
    CONSTRAINT fk_bundle_item_bundle FOREIGN KEY (bundle_id) REFERENCES course_bundles(id) ON DELETE CASCADE,
    CONSTRAINT fk_bundle_item_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);

-- 3. Add bundle_id to payments table (snapshot payment items)
ALTER TABLE payments
ADD COLUMN bundle_id BIGINT NULL,
ADD CONSTRAINT fk_payment_bundle FOREIGN KEY (bundle_id) REFERENCES course_bundles(id) ON DELETE SET NULL;

-- Indexes for query performance (MySQL không hỗ trợ CREATE INDEX IF NOT EXISTS)
CREATE INDEX idx_payments_course ON payments(course_id);
CREATE INDEX idx_payments_bundle ON payments(bundle_id);


