-- Mã ticket chuyên nghiệp SUP-{yyMMdd}-{NHÓM}-{id} (thay cho TKT-{id} suy ra từ id) và nội dung phản hồi
-- của Admin dạng JSON (tiêu đề từng mục tùy chỉnh). admin_steps (V150) không còn dùng nhưng giữ nguyên cột, không xóa dữ liệu cũ.
ALTER TABLE support_tickets
  ADD COLUMN code VARCHAR(40) NULL,
  ADD COLUMN admin_content TEXT NULL;

UPDATE support_tickets
SET code = CONCAT('SUP-', DATE_FORMAT(created_at, '%y%m%d'), '-',
    CASE category WHEN 'ACCOUNT' THEN 'ACC' WHEN 'PAYMENT' THEN 'PAY' WHEN 'COURSE' THEN 'CRS' WHEN 'LEARNING' THEN 'LRN' WHEN 'AI_FEATURES' THEN 'AIF' WHEN 'EXAM' THEN 'EXM' WHEN 'CERTIFICATE' THEN 'CRT' WHEN 'COMMUNITY' THEN 'COM' WHEN 'LIVE' THEN 'LIV' WHEN 'INSTRUCTOR' THEN 'INS' WHEN 'PRIVACY' THEN 'PRV' WHEN 'TECHNICAL' THEN 'TEC' ELSE 'OTH' END, '-', LPAD(id, 5, '0'));

ALTER TABLE support_tickets
  MODIFY COLUMN code VARCHAR(40) NOT NULL,
  ADD UNIQUE KEY uk_support_ticket_code (code);
