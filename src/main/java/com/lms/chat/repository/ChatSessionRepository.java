package com.lms.chat.repository;

import com.lms.chat.entity.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository cho {@link ChatSession}.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** UC30 — mỗi (học viên, khóa học) tái sử dụng lại đúng 1 phiên chat gần nhất, dùng
     * chung cho MỌI bài học trong khóa (06/09/2026 — trước đây tách riêng theo từng bài). */
    Optional<ChatSession> findFirstByUser_IdAndCourse_IdOrderByCreatedAtDesc(Long userId, Long courseId);

    /** UC30 mở rộng — danh sách TOÀN BỘ phiên chat của (học viên, khóa học) này, mới nhất
     * trước, để dựng danh sách "lịch sử trò chuyện" kiểu ChatGPT — dùng CHUNG cho mọi bài
     * học trong khóa, không tách theo từng bài như trước. */
    List<ChatSession> findByUser_IdAndCourse_IdOrderByCreatedAtDesc(Long userId, Long courseId);
}
