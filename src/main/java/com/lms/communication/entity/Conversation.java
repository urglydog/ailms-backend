package com.lms.communication.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Hộp thư 1-1 giữa 1 học viên và 1 giảng viên (tính năng "Giao tiếp" kiểu Udemy, 19/09/2026).
 * Mỗi cặp (học viên, giảng viên) chỉ có đúng 1 cuộc hội thoại duy nhất (UNIQUE) dù học viên có
 * nhắn về nhiều khóa khác nhau của cùng giảng viên đó — {@code course} chỉ là ngữ cảnh lúc TẠO
 * hội thoại (hiển thị "Về khóa X"), không giới hạn nội dung nhắn sau đó.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_student_instructor", columnNames = {"student_id", "instructor_id"}))
@Getter
@Setter
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt = LocalDateTime.now();
}
