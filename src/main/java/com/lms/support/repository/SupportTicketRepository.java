package com.lms.support.repository;

import com.lms.common.enums.SupportTicketStatus;
import com.lms.support.entity.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status);

    long countByStatus(SupportTicketStatus status);
}
