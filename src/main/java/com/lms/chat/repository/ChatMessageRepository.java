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

    /** Insight AI cho giảng viên (26/11/2026) — mọi câu hỏi USER của khoá từ {@code since}, kèm
     * tin AI trả lời ngay sau. Trả [content, user_id, context_lesson_id, ai_content, created_at],
     * mới nhất trước. Gom nhóm/ẩn danh hoá làm ở {@code TutorInsightService}. */
    @Query(value = """
            SELECT u.content, s.user_id, a.context_lesson_id, a.content AS ai_content, u.created_at
            FROM chat_messages u
            JOIN chat_sessions s ON s.id = u.chat_session_id
            LEFT JOIN chat_messages a ON a.id = (SELECT MIN(a2.id) FROM chat_messages a2
                   WHERE a2.chat_session_id = u.chat_session_id AND a2.id > u.id AND a2.sender = 'AI')
            WHERE u.sender = 'USER' AND s.course_id = :courseId AND u.created_at >= :since
            ORDER BY u.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findQuestionsForCourseInsight(
            @Param("courseId") Long courseId, @Param("since") java.time.LocalDateTime since, @Param("limit") int limit);

    /** Khoá có câu hỏi Gia sư mới từ {@code since} — job hằng ngày chỉ tóm tắt những khoá này. */
    @Query(value = """
            SELECT DISTINCT s.course_id FROM chat_messages u
            JOIN chat_sessions s ON s.id = u.chat_session_id
            WHERE u.sender = 'USER' AND u.created_at >= :since AND s.course_id IS NOT NULL
            """, nativeQuery = true)
    List<Long> findCourseIdsWithQuestionsSince(@Param("since") java.time.LocalDateTime since);
}
