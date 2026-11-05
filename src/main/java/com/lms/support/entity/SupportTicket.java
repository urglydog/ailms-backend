package com.lms.support.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.SupportTicketCategory;
import com.lms.common.enums.SupportTicketStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Yêu cầu hỗ trợ gửi tới Admin (05/11/2026) — gửi được cả khi CHƯA đăng nhập (vd quên mật khẩu,
 * không nhận được OTP) nên chỉ lưu {@link #email} người gửi, không FK về users. Mã ticket hiển
 * thị {@code TKT-000123} được suy ra từ id, không có cột riêng. Tối giản có chủ đích như
 * {@code PaymentDispute}: 1 field {@link #adminReply}, không có luồng hội thoại qua lại.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
public class SupportTicket extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SupportTicketCategory category;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    /** Các bước hướng dẫn trong phản hồi của Admin, mỗi bước 1 dòng (đã loại xuống dòng thừa trong từng bước). */
    @Column(name = "admin_steps", columnDefinition = "TEXT")
    private String adminSteps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
