package com.lms.chat.repository;

import com.lms.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Gợi ý câu hỏi phổ biến (26/11/2026) — câu hỏi USER mà tin AI trả lời NGAY SAU đó dùng
     * {@code lessonId} làm ngữ cảnh, trong khoá {@code courseId}. Trả [content, user_id], mới nhất
     * trước, tối đa {@code limit} dòng — gom nhóm/đếm làm ở service. */
    @Query(value = """
            SELECT u.content, s.user_id FROM chat_messages u
            JOIN chat_sessions s ON s.id = u.chat_session_id
            WHERE u.sender = 'USER' AND s.course_id = :courseId
              AND (SELECT a.context_lesson_id FROM chat_messages a
                   WHERE a.chat_session_id = u.chat_session_id AND a.id > u.id AND a.sender = 'AI'
                   ORDER BY a.id LIMIT 1) = :lessonId
            ORDER BY u.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findRecentQuestionsForLesson(
            @Param("courseId") Long courseId, @Param("lessonId") Long lessonId, @Param("limit") int limit);
}
