-- (19/09/2026, sửa lỗi) — tiếp nối V113: entity `CourseResource` còn field `isDeleted`
-- (`is_deleted`) cũng chưa từng có migration nào tạo cột này trong bảng thật, cùng nguyên nhân
-- V106 bị đổi thành no-op sai (xem V113). Thêm nốt trong 1 lần để tránh crash-loop lặp lại
-- từng cột một.
SELECT 1;
