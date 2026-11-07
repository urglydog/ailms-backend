package com.lms.support.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Ảnh minh chứng đính kèm ticket hỗ trợ (05/11/2026) — mirror {@code DisputeAttachment}, chỉ nhận ảnh. */
@Entity
@Table(name = "support_ticket_attachments")
@Getter
@Setter
public class SupportTicketAttachment extends BaseEntity {

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /** jpg / png / webp. */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;
}
