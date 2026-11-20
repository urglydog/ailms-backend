package com.lms.support.repository;

import com.lms.common.enums.SupportTicketStatus;
import com.lms.support.entity.SupportTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    /** Danh sách yêu cầu hỗ trợ cho admin (20/11/2026): lọc trạng thái + tìm theo mã/email/tiêu đề, luôn phân trang. */
    @org.springframework.data.jpa.repository.Query("SELECT t FROM SupportTicket t WHERE (:status IS NULL OR t.status = :status) "
            + "AND (:q IS NULL OR LOWER(t.code) LIKE :q OR LOWER(t.email) LIKE :q OR LOWER(t.subject) LIKE :q) ORDER BY t.createdAt DESC")
    List<SupportTicket> searchForAdmin(@org.springframework.data.repository.query.Param("status") SupportTicketStatus status,
                                       @org.springframework.data.repository.query.Param("q") String q,
                                       org.springframework.data.domain.Pageable pageable);

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status);

    long countByStatus(SupportTicketStatus status);
}
