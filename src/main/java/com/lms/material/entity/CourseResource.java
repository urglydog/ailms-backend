package com.lms.material.entity;

import com.lms.catalog.entity.Chapter;
import com.lms.catalog.entity.Course;
import com.lms.catalog.entity.Lesson;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "course_resources")
@Getter
@Setter
public class CourseResource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 50)
    private String fileType;
    
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
    
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
