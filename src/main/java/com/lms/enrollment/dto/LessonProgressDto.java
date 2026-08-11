package com.lms.enrollment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** UC21 — ghi nhận tiến độ xem 1 bài học. */
public class LessonProgressDto {

    public record RecordReq(
            /** Tổng số giây đã phát THẬT (tích lũy, không phải delta) — BE giữ max(cũ, mới). */
            @NotNull(message = "watchedSec không được để trống") @Min(value = 0, message = "watchedSec không được âm")
            Integer watchedSec,
            /** Vị trí phát hiện tại — luôn ghi đè, tua lùi xem lại là hợp lệ (BR-PROGRESS-03). */
            @NotNull(message = "lastPositionSec không được để trống") @Min(value = 0, message = "lastPositionSec không được âm")
            Integer lastPositionSec
    ) {}

    public record Res(
            Integer watchedSec,
            Integer lastPositionSec,
            Boolean isCompleted
    ) {}
}
