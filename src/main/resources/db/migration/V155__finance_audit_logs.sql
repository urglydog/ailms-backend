-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn A) — nhật ký kiểm toán tài chính, CHỈ THÊM DÒNG.
--
-- Ghi lại ai (actor), làm gì (action), lên đối tượng nào (entity), giá trị trước/sau, IP và
-- thời điểm cho mọi thay đổi liên quan tiền: đổi cấu hình tỷ lệ/hạn, hoàn tiền, chi trả, đổi
-- tài khoản nhận tiền... Không có khóa ngoại tới users có chủ đích: nhật ký phải tồn tại độc lập
-- ngay cả khi tài khoản bị xóa/đổi. Entity map @Immutable — ứng dụng không bao giờ UPDATE/DELETE.
CREATE TABLE finance_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    actor_email VARCHAR(255) NULL,
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(64) NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    INDEX idx_fal_entity (entity_type, entity_id),
    INDEX idx_fal_actor (actor_user_id),
    INDEX idx_fal_created (created_at)
);
