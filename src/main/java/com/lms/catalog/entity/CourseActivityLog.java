package com.lms.catalog.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhật ký hoạt động của giảng viên trên 1 khóa học (tạo/xóa học liệu, đổi phiên bản,
 * cập nhật thông tin khóa học...) — tham khảo panel "Recent Activity" của GakuNin RDM.
 * Chỉ ghi lại mô tả đã dựng sẵn (tiếng Việt), không cần bảng tra cứu action-type riêng.
 */
@Entity
@Table(name = "course_activity_logs")
@Getter
@Setter
public class CourseActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Người thực hiện hành động — có thể null nếu hệ thống tự động thực hiện. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "description", nullable = false, length = 500)
    private String description;
}
