package com.lms.communication.repository;

import com.lms.communication.entity.Message;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<Message> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    Optional<Message> findTopByConversation_IdOrderByCreatedAtDesc(Long conversationId);

    long countByConversation_IdAndIsReadFalseAndSender_IdNot(Long conversationId, Long readerId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true "
            + "WHERE m.conversation.id = :conversationId AND m.sender.id <> :readerId AND m.isRead = false")
    void markConversationRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);
}
