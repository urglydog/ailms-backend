-- Loại quiz người dùng chọn lúc tạo bằng AI (Quick Check hay Thi chính thức) — trước đây lựa chọn này không được gửi lên nên mọi quiz AI đều thành LECTURE_QUIZ
-- và đề thi có giám sát của giảng viên mở ra như Quick Check (21/11/2026). Chỉ THÊM cột cho phép trống: NULL = hành vi cũ (LECTURE_QUIZ), không đụng dữ liệu cũ.
ALTER TABLE material_generations ADD COLUMN quiz_type VARCHAR(20) NULL;
