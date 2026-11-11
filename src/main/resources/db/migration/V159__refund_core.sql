-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn B) — nền cho hoàn tiền thật. CHỈ THÊM cột/bảng.
--
-- payments.refunded_at: thời điểm hoàn. Trạng thái REFUNDED mới nằm ở enum PaymentStatus (cột status
-- là VARCHAR(20), không đổi kiểu) → mọi truy vấn `status = 'PAID'` (doanh thu giảng viên/admin/AI)
-- tự loại giao dịch đã hoàn mà không phải sửa từng query.
--
-- enrollments.revoked_*: thu hồi MỀM quyền học khi hoàn tiền. Không xóa dòng vì
-- certificates.enrollment_id có khóa ngoại và javadoc Enrollment cấm xóa ghi danh. Entity gắn
-- @SQLRestriction("revoked_at IS NULL") nên mọi truy vấn hiện có tự coi dòng đã thu hồi như không tồn
-- tại. UNIQUE (user_id, course_id) giữ nguyên → mua lại thì KÍCH HOẠT LẠI đúng dòng cũ.
--
-- payment_refunds: 1 dòng cho mỗi giao dịch đã hoàn (UNIQUE payment_id → chống hoàn 2 lần ở tầng DB),
-- ghi chế độ trách nhiệm, phần giảng viên bị trừ, và 2 bước tách bạch: ADMIN DUYỆT (PENDING_TRANSFER)
-- rồi ADMIN CHUYỂN KHOẢN TAY và nhập mã giao dịch (TRANSFERRED).
ALTER TABLE payments ADD COLUMN refunded_at DATETIME NULL;

ALTER TABLE enrollments
    ADD COLUMN revoked_at DATETIME NULL,
    ADD COLUMN revoked_reason VARCHAR(255) NULL,
    ADD COLUMN revoked_payment_id BIGINT NULL;

CREATE TABLE payment_refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    dispute_id BIGINT NULL,
    amount DECIMAL(14,2) NOT NULL,
    responsibility VARCHAR(30) NOT NULL,
    instructor_charge DECIMAL(14,2) NOT NULL,
    platform_share DECIMAL(14,2) NOT NULL,
    refund_bank_info VARCHAR(100) NULL,
    transfer_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_TRANSFER',
    transfer_ref VARCHAR(100) NULL,
    approved_by_user_id BIGINT NOT NULL,
    approved_at DATETIME NOT NULL,
    transferred_by_user_id BIGINT NULL,
    transferred_at DATETIME NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_pr_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_pr_dispute FOREIGN KEY (dispute_id) REFERENCES payment_disputes(id),
    CONSTRAINT fk_pr_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_pr_transferrer FOREIGN KEY (transferred_by_user_id) REFERENCES users(id),
    UNIQUE KEY uk_pr_payment (payment_id),
    INDEX idx_pr_transfer_status (transfer_status)
);
