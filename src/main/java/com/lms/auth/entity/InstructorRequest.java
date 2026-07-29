package com.lms.auth.entity;

import com.lms.common.entity.BaseEntity;
import com.lms.common.enums.RequestStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Yêu cầu nâng cấp từ Học viên lên Giảng viên (UC07 gửi, UC41 duyệt).
 *
 * <p>BR-ROLE-04: mỗi người chỉ có tối đa 1 bản {@code PENDING} tại một thời điểm;
 * bị từ chối phải chờ ít nhất 7 ngày mới được gửi lại. Sau khi được duyệt, tài khoản
 * <b>giữ nguyên</b> toàn bộ Enrollment và LessonProgress cũ.
 */
@Entity
@Table(name = "instructor_requests")
@Getter
@Setter
public class InstructorRequest extends BaseEntity {

    @Column(name = "motivation", columnDefinition = "TEXT")
    private String motivation;

    /** Link chứng chỉ / hồ sơ đính kèm. */
    @Column(name = "credential_url", length = 500)
    private String credentialUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    /** Bắt buộc có giá trị khi status = REJECTED. */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
