package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * UC-ANTICHEAT (25/09/2026) — audit trail vĩnh viễn cho 1 vi phạm trong lúc làm bài thi
 * proctored. Trước đây (chưa có bảng này) mọi vi phạm chỉ tồn tại ở FE {@code localStorage},
 * mất sạch khi đóng tab/sửa JS — giảng viên không có cách nào xem lại. Xem
 * {@code QuizService.recordViolation}.
 *
 * <p>{@code type} là 1 trong: TAB_SWITCH, WINDOW_BLUR, FULLSCREEN_EXIT, NO_FACE,
 * MULTIPLE_FACES, HEAD_TURNED, GAZE_AWAY (Gemini Vision đánh giá hướng nhìn),
 * AUDIO_VOICE_DETECTED, DEVTOOLS_OPEN, COPY_PASTE_BLOCKED, IDLE_TOO_LONG.
 */
@Entity
@Table(name = "quiz_attempt_violations")
@Getter
@Setter
public class QuizAttemptViolation extends BaseEntity {

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;
}
