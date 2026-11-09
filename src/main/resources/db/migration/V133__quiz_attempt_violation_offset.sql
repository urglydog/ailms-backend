-- Sua loi lech ~5s giua vi pham va video (27/09/2026) — luu san offset FE tu tinh tai thoi diem
-- phat hien (cung dong ho voi luc bat dau ghi hinh), thay vi BE suy nguoc qua submittedAt -
-- durationSec (phu thuoc do tre round-trip API nop bai, khong on dinh).
ALTER TABLE quiz_attempt_violations
    ADD COLUMN offset_sec INT NULL;
