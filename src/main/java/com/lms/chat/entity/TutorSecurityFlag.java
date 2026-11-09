package com.lms.chat.entity;

import com.lms.auth.entity.User;
import com.lms.catalog.entity.Course;
import com.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 1 lần tin nhắn học viên gửi vào Socratic Tutor Agent khớp 1 {@link TutorSecurityPattern} nghi
 * vấn (BR-TUTOR-SEC-06). CHỈ để Admin xem lại/theo dõi — KHÔNG tự động khóa tài khoản hay chặn
 * cứng câu hỏi ở v1 (tránh false-positive chặn nhầm học viên hỏi hợp lệ, xem
 * {@code com.lms.chat.service.TutorSecurityService}); câu hỏi vẫn được gửi tiếp cho AI Worker
 * bình thường sau khi ghi log.
 */
@Entity
@Table(name = "tutor_security_flags")
@Getter
@Setter
public class TutorSecurityFlag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Mô tả pattern nào khớp ({@link TutorSecurityPattern#getDescription()} tại thời điểm khớp —
     * KHÔNG tham chiếu FK, vì pattern có thể bị Admin sửa/xoá sau này mà log cũ vẫn phải giữ
     * nguyên nội dung đã ghi nhận lúc đó). */
    @Column(name = "matched_pattern", nullable = false)
    private String matchedPattern;

    /** Nội dung tin nhắn học viên gửi (để Admin review) — nguyên văn, KHÔNG cắt bớt. */
    @Column(name = "message_snapshot", columnDefinition = "TEXT", nullable = false)
    private String messageSnapshot;
}
