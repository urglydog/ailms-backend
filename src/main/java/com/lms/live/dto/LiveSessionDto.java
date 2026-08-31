package com.lms.live.dto;

import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.enums.LiveVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class LiveSessionDto {

    /**
     * UC50 — tạo/lên lịch phiên Live. {@code sourceLanguage} để trống thì lấy theo
     * {@code preferredLanguage} của giảng viên (BR-LIVE-04). {@code scheduledAt} để trống
     * nghĩa là "sẵn sàng bắt đầu bất kỳ lúc nào", không phải lỗi.
     */
    public record CreateReq(
            @NotNull(message = "Khóa học không được để trống")
            Long courseId,
            @NotBlank(message = "Tiêu đề không được để trống")
            String title,
            @NotNull(message = "Phạm vi hiển thị không được để trống")
            LiveVisibility visibility,
            String sourceLanguage,
            LocalDateTime scheduledAt
    ) {}

    public record Res(
            Long id,
            String title,
            LiveVisibility visibility,
            LiveSessionStatus status,
            String roomName,
            String sourceLanguage,
            LocalDateTime scheduledAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Long courseId,
            String courseTitle
    ) {}

    /** UC50 — trả về ngay sau khi bấm "Bắt đầu Live", đủ để FE connect LiveKit React SDK. */
    public record StartRes(
            String accessToken,
            String serverUrl,
            String roomName,
            String identity
    ) {}
}
