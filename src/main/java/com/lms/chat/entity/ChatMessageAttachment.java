package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 1 tệp học viên đính kèm khi hỏi Gia sư AI (UC30 mở rộng) — luôn gắn vào tin nhắn
 * {@code sender = USER}, không bao giờ gắn vào tin AI (AI không đính kèm gì cả).
 *
 * <p>Lưu trên Backblaze B2 giống {@code LessonDocument} — CHỈ hiển thị lại (xem inline
 * trong khung chat) khi phục hồi lịch sử, KHÔNG cấp link tải xuống (đỡ tốn hạn mức băng
 * thông B2 cho hành vi "tải lại nhiều lần" — xem {@code TutorController}).
 */
@Entity
@Table(name = "chat_message_attachments")
@Getter
@Setter
public class ChatMessageAttachment extends BaseEntity {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /** MIME thật, xác định bằng magic number (Tika) — KHÔNG tin Content-Type client gửi lên. */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_message_id", nullable = false)
    private ChatMessage chatMessage;
}
