package com.lms.communication.entity;

import com.lms.catalog.entity.Lesson;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Bài tập tự luận/nộp file gắn với 1 bài học, Giảng viên chấm điểm + phản hồi thủ công (tính
 * năng "Giao tiếp" kiểu Udemy, 19/09/2026) — khác hẳn {@code Quiz} (trắc nghiệm tự chấm) và
 * {@code MaterialAssignment} (chỉ là bảng gán học liệu AI vào khóa/chương/bài, không phải nơi
 * học viên nộp bài). Đặt tên {@code CourseAssignment} để tránh nhầm với 2 khái niệm đó.
 */
@Entity
@Table(name = "course_assignments")
@Getter
@Setter
public class CourseAssignment extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "max_score")
    private Integer maxScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
