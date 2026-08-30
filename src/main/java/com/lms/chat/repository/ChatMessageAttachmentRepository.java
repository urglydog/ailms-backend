package com.lms.chat.repository;

import com.lms.chat.entity.ChatMessageAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link ChatMessageAttachment}. */
@Repository
public interface ChatMessageAttachmentRepository extends JpaRepository<ChatMessageAttachment, Long> {

    /** UC30 mở rộng — nạp tệp đính kèm của NHIỀU tin nhắn cùng lúc khi phục hồi lịch sử,
     * tránh N+1 query (1 lần gọi cho cả đoạn hội thoại thay vì 1 lần/tin nhắn). */
    List<ChatMessageAttachment> findByChatMessage_IdIn(List<Long> chatMessageIds);
}
