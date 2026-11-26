package com.lms.community.repository;

import com.lms.community.entity.LessonChat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonChatRepository extends JpaRepository<LessonChat, String> {
    List<LessonChat> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    /** Bảng điều khiển "Giao tiếp > Hỏi đáp" của Giảng viên (19/09/2026) — mỗi tin nhắn GỐC
     * (parent == null) trong 1 bài học coi như 1 "câu hỏi", tin trả lời là các `LessonChat` có
     * `parent` trỏ tới nó (xem {@link com.lms.community.service.LessonChatService}). */
    @EntityGraph(attributePaths = {"lesson", "lesson.chapter", "lesson.chapter.course", "user"})
    Page<LessonChat> findByParentIsNullAndLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(
            String email, Pageable pageable);

    @EntityGraph(attributePaths = {"lesson", "lesson.chapter", "lesson.chapter.course", "user"})
    Page<LessonChat> findByParentIsNullAndLesson_Chapter_Course_IdAndLesson_Chapter_Course_Instructor_EmailOrderByCreatedAtDesc(
            Long courseId, String email, Pageable pageable);

    /**
     * Hộp thư Hỏi đáp của giảng viên (21/11/2026): lọc khóa học, "chưa có ai trả lời" và tìm kiếm NGAY TRONG truy vấn để phân trang đúng
     * (trước đây lọc/tìm sau khi đã lấy trang nên trang có thể thiếu hoặc không tìm thấy câu hỏi ở trang khác). {@code search} là mẫu LIKE chữ thường hoặc null.
     */
    @EntityGraph(attributePaths = {"lesson", "lesson.chapter", "lesson.chapter.course", "user"})
    @org.springframework.data.jpa.repository.Query(
            "SELECT q FROM LessonChat q JOIN q.lesson l JOIN l.chapter ch JOIN ch.course c "
            + "WHERE q.parent IS NULL AND c.instructor.email = :email AND (:courseId IS NULL OR c.id = :courseId) "
            + "AND (:unanswered = false OR NOT EXISTS (SELECT 1 FROM LessonChat r WHERE r.parent = q)) "
            + "AND (:noInstructor = false OR NOT EXISTS (SELECT 1 FROM LessonChat ri WHERE ri.parent = q AND ri.user = c.instructor)) "
            + "AND (:search IS NULL OR LOWER(q.content) LIKE :search OR LOWER(q.userName) LIKE :search OR LOWER(l.title) LIKE :search OR LOWER(c.title) LIKE :search)")
    Page<LessonChat> searchQuestionsForInstructor(
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("courseId") Long courseId,
            @org.springframework.data.repository.query.Param("unanswered") boolean unanswered,
            @org.springframework.data.repository.query.Param("noInstructor") boolean noInstructor,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    List<LessonChat> findByParent_IdOrderByCreatedAtAsc(String parentId);

    long countByParent_Id(String parentId);
}
