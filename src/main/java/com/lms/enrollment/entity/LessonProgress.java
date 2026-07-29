package com.lms.enrollment.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.auth.entity.User;
import com.lms.catalog.entity.Lesson;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tiến độ xem của một học viên trên một bài học (UC21).
 *
 * <p>BR-PROGRESS-01: {@code watchedSec} là tổng thời gian <b>phát thực tế</b>,
 * <b>không cộng đoạn tua nhanh</b>. Đạt >= 90% thời lượng thì {@code isCompleted}
 * chuyển TRUE và <b>không bao giờ tự hủy</b> khi học viên xem lại (một chiều).
 */
@Entity
@Table(name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_progress_user_lesson", columnNames = {"user_id", "lesson_id"}))
@Getter
@Setter
public class LessonProgress extends BaseEntity {

    /** Khôi phục đúng vị trí khi học viên quay lại (BR-PROGRESS-03). */
    @Column(name = "last_position_sec", nullable = false)
    private Integer lastPositionSec = 0;

    /** KHÔNG cộng đoạn tua nhanh bỏ qua (BR-PROGRESS-01). */
    @Column(name = "watched_sec", nullable = false)
    private Integer watchedSec = 0;

    /** Chuyển một chiều khi đạt ngưỡng 90% (BR-PROGRESS-01). */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
