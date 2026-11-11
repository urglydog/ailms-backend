-- (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn A) — SỔ CÁI thu nhập giảng viên, chỉ thêm dòng.
--
-- `amount` có dấu: EARNING (+) khi đơn PAID; về sau REFUND_REVERSAL (−) khi hoàn tiền,
-- PAYOUT_RESERVE (−) khi yêu cầu rút, PAYOUT_RELEASE (+) khi rút bị hủy/từ chối, ADJUSTMENT.
-- `available_at` = thời điểm khoản đó được tính vào số dư khả dụng (hết thời gian giữ tiền).
-- UNIQUE (payment_id, entry_type): mỗi đơn chỉ có tối đa 1 dòng mỗi loại → ghi lại nhiều lần
-- (IPN gửi trùng, chạy lại backfill) không nhân đôi tiền. payment_id NULL được lặp (MySQL cho phép
-- nhiều NULL trong UNIQUE) dùng cho dòng không gắn đơn (rút tiền, điều chỉnh tay).
-- payout_id/ref_entry_id chưa có khóa ngoại: bảng payouts thêm ở Giai đoạn C.
CREATE TABLE instructor_ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instructor_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    available_at DATETIME NOT NULL,
    payment_id BIGINT NULL,
    payout_id BIGINT NULL,
    ref_entry_id BIGINT NULL,
    note VARCHAR(255) NULL,
    created_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT fk_ile_instructor FOREIGN KEY (instructor_id) REFERENCES users(id),
    CONSTRAINT fk_ile_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    UNIQUE KEY uk_ile_payment_type (payment_id, entry_type),
    INDEX idx_ile_instructor_available (instructor_id, available_at)
);
