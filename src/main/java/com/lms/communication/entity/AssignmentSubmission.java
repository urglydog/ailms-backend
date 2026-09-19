package com.lms.communication.entity;

import com.lms.auth.entity.User;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "assignment_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_submissions_assignment_student", columnNames = {"assignment_id", "student_id"}))
@Getter
@Setter
public class AssignmentSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private CourseAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    /** null = chưa chấm. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;
}
