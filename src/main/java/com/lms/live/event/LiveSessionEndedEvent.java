package com.lms.live.event;

/**
 * Publish ngay khi {@code LiveSession.status} chuyển sang {@code ENDED} (dù chủ động bấm
 * kết thúc hay tự động qua BR-LIVE-09) — chưa có listener nào ở F11.1. F11.3 (LiveLanguageTrack)
 * sẽ subscribe event này để dừng mọi track dịch còn ACTIVE của phiên, thay vì F11.1 phải biết
 * trước về entity của F11.3 chưa tồn tại.
 */
public record LiveSessionEndedEvent(Long liveSessionId) {}
