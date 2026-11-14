-- Phase 2 chi tự động (14/11/2026): tài khoản nhận hoàn tiền của học viên khai báo CÓ CẤU TRÚC thay cho 1 ô chữ tự do, kèm xác nhận
-- "đây là tài khoản của chính tôi". Cột refund_bank_account (chữ tự do) giữ nguyên cho client cũ/mobile và để hiển thị.
ALTER TABLE payment_disputes
    ADD COLUMN refund_bank_code VARCHAR(20) NULL,
    ADD COLUMN refund_account_number VARCHAR(30) NULL,
    ADD COLUMN refund_account_holder VARCHAR(100) NULL,
    ADD COLUMN refund_account_confirmed TINYINT(1) NOT NULL DEFAULT 0;
