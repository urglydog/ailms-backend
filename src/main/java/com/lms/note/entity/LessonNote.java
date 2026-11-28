package com.lms.note.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ghi chú cá nhân gắn mốc thời gian video (26/11/2026, mở rộng ngoài đặc tả gốc). */
@Entity
@Table(name = "lesson_notes")
@Getter
@Setter
public class LessonNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "timestamp_sec", nullable = false)
    private Integer timestampSec = 0;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
}
