package com.lms.chat.repository;

import com.lms.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link ChatMessage}.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** UC30 mở rộng — lấy N tin gần nhất của 1 phiên (mới nhất trước) để dựng lịch sử hội thoại
     * gửi cho Gemini; gọi tại chỗ trả về đảo ngược lại (cũ → mới) trước khi dùng. */
    List<ChatMessage> findByChatSession_IdOrderByCreatedAtDesc(Long chatSessionId, Pageable pageable);

    /** UC30 mở rộng — TOÀN BỘ tin của 1 phiên, cũ → mới, để phục hồi lại cuộc trò chuyện trên
     * UI (khác bản có {@code Pageable} ở trên — bản đó dùng cho ngữ cảnh gửi Gemini, có giới hạn). */
    List<ChatMessage> findByChatSession_IdOrderByCreatedAtAsc(Long chatSessionId);
}
