-- UC30 mở rộng — học viên đính kèm ảnh/tài liệu/mã nguồn khi hỏi Gia sư AI. Tệp lưu trên
-- Backblaze B2 (giống lesson_documents), 1 tin nhắn có thể có NHIỀU tệp nên tách bảng con
-- thay vì thêm cột đơn lẻ vào chat_messages.
CREATE TABLE chat_message_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    chat_message_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_attachments_message FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_chat_message_attachments_message ON chat_message_attachments (chat_message_id);
