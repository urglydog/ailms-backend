package com.lms.notification.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Thông báo hệ thống (UC08).
 *
 * <p>BR-NOTIFY-01: mọi sự kiện phải <b>vừa</b> bắn WebSocket cho người đang online
 * <b>vừa</b> lưu bản ghi này cho người offline xem lại. Chỉ bắn WebSocket mà không
 * lưu DB là bug. Thông báo đã đọc giữ tối đa 90 ngày rồi tự dọn.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    /** Ví dụ: DUBBING_COMPLETED, DUBBING_FAILED, COURSE_APPROVED, MATERIAL_READY. */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Điều hướng khi người dùng bấm vào thông báo. */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
