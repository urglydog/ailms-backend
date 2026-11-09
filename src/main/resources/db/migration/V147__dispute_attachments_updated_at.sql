-- Sửa bug thật (23/10/2026): V146 tạo bảng dispute_attachments thiếu cột updated_at mà
-- DisputeAttachment extends BaseEntity bắt buộc phải có (@LastModifiedDate) — Hibernate
-- schema-validation chặn app khởi động hoàn toàn từ lúc migrate, port 8080 không mở được.
ALTER TABLE dispute_attachments
  ADD COLUMN updated_at DATETIME NULL;
