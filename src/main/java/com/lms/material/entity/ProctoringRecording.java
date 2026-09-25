package com.lms.material.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * UC-ANTICHEAT (25/09/2026) — video bằng chứng (màn hình + webcam ghép cạnh nhau, ghi bằng
 * canvas-composite + MediaRecorder phía FE) cho 1 lượt thi proctored, upload lúc nộp bài.
 * Cảnh báo AI ({@link QuizAttemptViolation}) chỉ là marker hỗ trợ; video mới là bằng chứng
 * cuối cùng giảng viên xem lại khi có tranh chấp — đúng cách các tool proctoring thật làm.
 *
 * <p>{@code videoUrl} trỏ vào key dạng {@code proctoring/{attemptId}/{uuid}.webm} trên B2 —
 * KHÔNG bao giờ nhúng tiêu đề bài thi/ngày giờ vào tên file (đúng quy ước đặt tên hiện có của
 * dự án, xem các nơi khác dùng {@code StorageService}); màn hình quản lý hiển thị tên/ngày giờ
 * từ dữ liệu DB (join qua {@code attempt}), không suy ra từ filename.
 */
@Entity
@Table(name = "proctoring_recordings")
@Getter
@Setter
public class ProctoringRecording extends BaseEntity {

    @Column(name = "video_url", nullable = false, length = 1000)
    private String videoUrl;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private QuizAttempt attempt;
}
