-- (19/09/2026, sửa lỗi) — V106 từng bị đổi thành no-op (`SELECT 1;`) với giả định cột này
-- "đã có sẵn từ trước" (thêm thủ công hoặc từ 1 migration dở dang khác), nhưng giả định đó SAI:
-- bảng `course_resources` thực tế KHÔNG có cột `deleted_at`, trong khi entity `CourseResource`
-- vẫn khai báo field này — khiến Hibernate validate schema lúc khởi động LUÔN THẤT BẠI
-- ("missing column [deleted_at] in table [course_resources]"), backend crash-loop liên tục.
-- Flyway đã ghi V106 là "success" nên không tự chạy lại được — phải thêm cột ở migration MỚI này.
ALTER TABLE course_resources ADD COLUMN deleted_at DATETIME NULL;
