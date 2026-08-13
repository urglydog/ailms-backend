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
}
