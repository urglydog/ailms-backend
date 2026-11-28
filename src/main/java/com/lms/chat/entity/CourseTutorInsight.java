package com.lms.chat.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Tóm tắt + đề xuất AI cho giảng viên theo khoá (Insight AI, 26/11/2026). */
@Entity
@Table(name = "course_tutor_insights")
@Getter
@Setter
public class CourseTutorInsight extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    @Column(name = "course_id", nullable = false, unique = true)
    private Long courseId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = PENDING;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Mảng JSON các đề xuất hành động (chuỗi). */
    @Column(name = "actions", columnDefinition = "TEXT")
    private String actions;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
