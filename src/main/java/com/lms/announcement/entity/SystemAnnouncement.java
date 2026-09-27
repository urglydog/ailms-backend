package com.lms.announcement.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.AnnouncementAudience;
import com.lms.common.enums.AnnouncementDispatchStatus;
import com.lms.common.enums.AnnouncementSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Thông báo hệ thống do Admin soạn, phát tán (fan-out) tới toàn hệ thống/Giảng viên/Học viên/1 cá
 * nhân — xử lý NỀN qua hàng đợi Redis ({@code SystemAnnouncementService}), không đồng bộ trong
 * request tạo. KHÁC {@code com.lms.communication.entity.Announcement} (thông báo của GIẢNG VIÊN
 * gửi riêng cho học viên 1 khóa học cụ thể) — 2 khái niệm độc lập, cố tình đặt tên khác nhau và ở
 * 2 package khác nhau để không nhầm lẫn.
 *
 * <p>{@code severity=HIGH} còn được hiển thị thành banner cố định đầu trang (vd "Bảo trì hệ
 * thống") — xem {@code SystemAnnouncementController#getActiveBanner}.
 */
@Entity
@Table(name = "system_announcements")
@Getter
@Setter
public class SystemAnnouncement extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AnnouncementSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 20)
    private AnnouncementAudience audience;

    /** Chỉ có giá trị khi {@code audience = SPECIFIC_USER}. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** {@code null} = banner hiển thị vô thời hạn (chỉ áp dụng khi {@code severity = HIGH}) cho
     * tới khi có thông báo HIGH mới hơn thay thế. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", nullable = false, length = 20)
    private AnnouncementDispatchStatus dispatchStatus = AnnouncementDispatchStatus.PENDING;

    @Column(name = "total_recipients")
    private Integer totalRecipients;

    @Column(name = "sent_count", nullable = false)
    private Integer sentCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    private User createdByAdmin;
}
