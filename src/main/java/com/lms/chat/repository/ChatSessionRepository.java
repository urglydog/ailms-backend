package com.lms.chat.repository;

import com.lms.chat.entity.ChatSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link ChatSession}.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** UC30 — mỗi (học viên, bài học) tái sử dụng lại đúng 1 phiên chat gần nhất. */
    Optional<ChatSession> findFirstByUser_IdAndLesson_IdOrderByCreatedAtDesc(Long userId, Long lessonId);

    /** UC30 mở rộng — danh sách TOÀN BỘ phiên chat của (học viên, bài học), mới nhất trước,
     * để dựng danh sách "lịch sử trò chuyện" kiểu ChatGPT. */
    java.util.List<ChatSession> findByUser_IdAndLesson_IdOrderByCreatedAtDesc(Long userId, Long lessonId);
}
