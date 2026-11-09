package com.lms.catalog.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Chương trong khóa học (UC32).
 *
 * <p>Quan hệ Composition với Course: xoá Course thì xoá hết Chapter. Điều kiện gửi
 * kiểm duyệt cần tối thiểu 1 chương (BR-COURSE-01).
 */
@Entity
@Table(name = "chapters")
@Getter
@Setter
public class Chapter extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Mục tiêu học tập của phần này (15/09/2026, mở rộng — giao diện tham khảo Udemy "Sau khi
     * hoàn thành phần này, sinh viên sẽ có thể làm được những gì?"). */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @OneToMany(mappedBy = "chapter", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private java.util.List<Lesson> lessons = new java.util.ArrayList<>();
}
