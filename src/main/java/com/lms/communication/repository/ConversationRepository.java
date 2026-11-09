package com.lms.communication.repository;

import com.lms.communication.entity.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Kiểm quyền subscribe WebSocket {@code /topic/conversations/{id}} — chỉ 2 người trong hội thoại. */
    @org.springframework.data.jpa.repository.Query("select count(c) > 0 from Conversation c where c.id = :id"
            + " and (c.student.id = :userId or c.instructor.id = :userId)")
    boolean isParticipant(@org.springframework.data.repository.query.Param("id") Long id,
                          @org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<Conversation> findByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);

    @EntityGraph(attributePaths = {"student", "course"})
    List<Conversation> findByInstructor_EmailOrderByLastMessageAtDesc(String email);

    @EntityGraph(attributePaths = {"instructor", "course"})
    List<Conversation> findByStudent_EmailOrderByLastMessageAtDesc(String email);
}
