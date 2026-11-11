package com.lms.finance.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Nhật ký kiểm toán tài chính (11/11/2026, Epic hoàn tiền – chi trả) — CHỈ THÊM DÒNG.
 * {@code @Immutable}: Hibernate không bao giờ UPDATE dòng đã ghi. Cố ý không có khóa ngoại tới
 * {@code users} để nhật ký tồn tại độc lập khi tài khoản bị xóa/đổi.
 */
@Entity
@Immutable
@Table(name = "finance_audit_logs")
@Getter
@Setter
public class FinanceAuditLog extends BaseEntity {

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    /** ADMIN / INSTRUCTOR / STUDENT / SYSTEM (job, webhook không có người dùng). */
    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
