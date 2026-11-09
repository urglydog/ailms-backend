package com.lms.communication.entity;

import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Thông báo Giảng viên gửi tới toàn bộ học viên đã ghi danh 1 khóa học (tính năng "Giao tiếp"
 * kiểu Udemy, 19/09/2026). Tạo xong sẽ bắn {@link com.lms.common.service.NotificationService}
 * tới từng học viên (BR-NOTIFY-01) — bảng này chỉ lưu nội dung gốc để hiển thị lại trên trang
 * khóa học, không phải nơi tra cứu đã đọc/chưa đọc (đó là việc của bảng notifications).
 */
@Entity
@Table(name = "announcements")
@Getter
@Setter
public class Announcement extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
