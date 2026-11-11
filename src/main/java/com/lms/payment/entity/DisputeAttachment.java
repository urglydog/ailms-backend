package com.lms.payment.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Minh chứng (ảnh/video) đính kèm 1 khiếu nại giao dịch (23/10/2026) — mirror đúng
 * {@code com.lms.catalog.entity.LessonDocument}, FK về {@link PaymentDispute}.
 */
@Entity
@Table(name = "dispute_attachments")
@Getter
@Setter
public class DisputeAttachment extends BaseEntity {

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /** jpg / png / webp / mp4 / webm. */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private PaymentDispute dispute;

    /** Minh chứng của học viên hay của giảng viên (11/11/2026, V160). Mặc định STUDENT. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "side", nullable = false, length = 12)
    private com.lms.common.enums.AttachmentSide side = com.lms.common.enums.AttachmentSide.STUDENT;
}
