package com.lms.communication.repository;

import com.lms.communication.entity.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);

    @EntityGraph(attributePaths = {"student", "course"})
    List<Conversation> findByInstructor_EmailOrderByLastMessageAtDesc(String email);

    @EntityGraph(attributePaths = {"instructor", "course"})
    List<Conversation> findByStudent_EmailOrderByLastMessageAtDesc(String email);
}
