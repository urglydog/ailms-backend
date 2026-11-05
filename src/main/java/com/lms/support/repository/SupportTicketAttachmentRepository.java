package com.lms.support.repository;

import com.lms.support.entity.SupportTicketAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketAttachmentRepository extends JpaRepository<SupportTicketAttachment, Long> {
    List<SupportTicketAttachment> findByTicket_IdOrderByIdAsc(Long ticketId);
}
