-- (19/09/2026) — "Gỡ bỏ khóa học" trong Cài đặt KHÔNG BAO GIỜ xóa thật (đúng nghiệp vụ LMS:
-- không được phép mất dữ liệu khóa học của Giảng viên) — chỉ chuyển trạng thái sang ARCHIVED,
-- lưu lại trạng thái TRƯỚC ĐÓ vào cột này để "Kích hoạt lại" khôi phục đúng (DRAFT nếu chưa
-- từng xuất bản, PUBLISHED nếu đã từng xuất bản).
ALTER TABLE courses ADD COLUMN previous_status VARCHAR(20) NULL;
