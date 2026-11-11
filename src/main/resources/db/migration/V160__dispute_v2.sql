-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn B) — khiếu nại v2: phân loại, điều kiện hoàn,
-- giảng viên phản hồi. CHỈ THÊM cột; 3 khiếu nại cũ giữ nguyên (các cột mới NULL = chưa áp dụng).
--
-- category: lý do chuẩn hóa (CHANGED_MIND, CONTENT_MISMATCH, TECHNICAL_ERROR, DOUBLE_CHARGE, NOT_ENROLLED, OTHER).
-- eligibility: AUTO_ELIGIBLE / OUT_OF_POLICY / EXCEPTION — tính lúc gửi, admin vẫn quyết định cuối.
-- instructor_*: giảng viên được báo và có hạn (instructor_deadline_at) để phản hồi kèm minh chứng.
-- dispute_attachments.side: minh chứng của học viên hay của giảng viên.
ALTER TABLE payment_disputes
    ADD COLUMN category VARCHAR(30) NULL,
    ADD COLUMN eligibility VARCHAR(20) NULL,
    ADD COLUMN progress_pct_at_filing DECIMAL(5,2) NULL,
    ADD COLUMN instructor_decision VARCHAR(20) NULL,
    ADD COLUMN instructor_response TEXT NULL,
    ADD COLUMN instructor_responded_at DATETIME NULL,
    ADD COLUMN instructor_deadline_at DATETIME NULL;

ALTER TABLE dispute_attachments ADD COLUMN side VARCHAR(12) NOT NULL DEFAULT 'STUDENT';

CREATE INDEX idx_dispute_instructor_deadline ON payment_disputes (instructor_decision, instructor_deadline_at);
