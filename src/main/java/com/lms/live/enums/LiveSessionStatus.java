package com.lms.live.enums;

/**
 * Vòng đời phiên Live (UC50).
 *
 * <p>{@code SCHEDULED -> LIVE -> ENDED}, một chiều — không quay lui. Chuyển sang
 * {@code ENDED} có thể do giảng viên chủ động bấm kết thúc, hoặc tự động sau 60s
 * mất kết nối (BR-LIVE-09, xem {@code LiveSessionCronJob}).
 */
public enum LiveSessionStatus {
    SCHEDULED,
    LIVE,
    ENDED
}
