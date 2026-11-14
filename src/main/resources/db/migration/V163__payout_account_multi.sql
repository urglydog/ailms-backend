-- Nhiều tài khoản nhận tiền cho mỗi giảng viên (tối đa 5 ACTIVE, kiểm ở tầng service): is_default đánh dấu tài khoản mặc định dùng để rút;
-- replaces_account_id: bản chờ xác nhận do SỬA tài khoản đang ACTIVE (xác nhận xong thì bản cũ chuyển REPLACED, bản mới kế thừa cờ mặc định).
-- Không còn khóa rút sau khi đổi tài khoản (usable_from chỉ còn là mốc xác nhận) — theo quyết định 14/11/2026.
-- Trạng thái mới REMOVED (giảng viên gỡ) là chuỗi trong cột status sẵn có, không cần đổi cấu trúc.
ALTER TABLE instructor_payout_accounts
    ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN replaces_account_id BIGINT NULL;

-- Tài khoản ACTIVE hiện có (mỗi người tối đa 1) trở thành mặc định.
UPDATE instructor_payout_accounts SET is_default = 1 WHERE status = 'ACTIVE';
