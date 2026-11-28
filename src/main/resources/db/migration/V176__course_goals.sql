-- "Học viên sẽ học được gì" / "Yêu cầu" / "Đối tượng" (26/11/2026, mở rộng ngoài đặc tả gốc).
-- Mỗi cột lưu MẢNG JSON chuỗi (vd ["...", "..."]); NULL = chưa điền.
ALTER TABLE courses
    ADD COLUMN learning_outcomes TEXT NULL,
    ADD COLUMN requirements TEXT NULL,
    ADD COLUMN target_audience TEXT NULL;
