-- UpComming_Plan.md A1 — mã xác thực chứng chỉ hoàn thành khóa học. Không tạo bảng Certificate
-- riêng: chứng chỉ được sinh PDF on-the-fly từ dữ liệu enrollment đã có, cột này chỉ giữ mã UUID
-- ổn định (sinh 1 lần khi completedAt vừa được set) để in lên PDF làm mã xác thực.
ALTER TABLE enrollments ADD COLUMN certificate_code VARCHAR(36) NULL;
