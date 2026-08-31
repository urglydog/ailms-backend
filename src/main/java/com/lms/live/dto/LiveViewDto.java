package com.lms.live.dto;

import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import java.time.LocalDateTime;

public class LiveViewDto {

    /** UC51 — 1 dòng trong danh sách phiên LIVE/SCHEDULED của 1 khóa học (đã lọc theo BR-LIVE-01). */
    public record SummaryRes(
            Long id,
            String title,
            LiveSessionStatus status,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt
    ) {}

    /**
     * UC51 — chi tiết 1 phiên cho người XEM. {@code viewerToken}/{@code serverUrl}/{@code roomName}
     * chỉ có giá trị khi {@code status = LIVE} (chưa/không còn gì để connect khi SCHEDULED/ENDED).
     */
    public record DetailRes(
            Long id,
            String title,
            LiveVisibility visibility,
            LiveSessionStatus status,
            String sourceLanguage,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Long courseId,
            String courseTitle,
            String viewerToken,
            String serverUrl,
            String roomName
    ) {}
}
