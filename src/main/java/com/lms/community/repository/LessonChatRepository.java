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

    @EntityGraph(attributePaths = {"user"})
    List<LessonChat> findByParent_IdOrderByCreatedAtAsc(String parentId);

    long countByParent_Id(String parentId);
}
