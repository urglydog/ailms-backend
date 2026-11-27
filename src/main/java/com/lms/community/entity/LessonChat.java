package com.lms.community.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Lesson;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "lesson_chats")
@Getter
@Setter
public class LessonChat {

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private LessonChat parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Hỏi đáp (27/11/2026): lúc giảng viên sở hữu khoá mở câu hỏi; null = chưa đọc. Chỉ dùng trên tin GỐC. */
    @Column(name = "instructor_read_at")
    private Instant instructorReadAt;

    /** Hỏi đáp (27/11/2026): id tin trả lời được đánh dấu "hay nhất"; chỉ dùng trên tin GỐC. */
    @Column(name = "best_answer_id", length = 36)
    private String bestAnswerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
