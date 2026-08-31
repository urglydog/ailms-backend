package com.lms.live.dto;

import com.lms.live.enums.LiveTrackStatus;
import jakarta.validation.constraints.NotBlank;

public class LiveLanguageTrackDto {

    /**
     * UC52 — kích hoạt/tham gia 1 ngôn ngữ. {@code voiceName} chỉ có tác dụng khi đây là track
     * MỚI (chưa có track {@code ACTIVE} nào cho ngôn ngữ này) — nếu đã có, giá trị gửi lên bị bỏ
     * qua, trả về giọng đang phát thật (BR-LIVE-05).
     */
    public record ActivateReq(
            @NotBlank(message = "Ngôn ngữ đích không được để trống")
            String targetLanguage,
            String voiceName
    ) {}

    public record Res(
            Long id,
            String targetLanguage,
            String voiceName,
            LiveTrackStatus status,
            Integer activeListenerCount,
            /** Tên track LiveKit để FE subscribe đúng luồng dịch — chỉ có khi vừa activate thành công. */
            String trackName
    ) {}
}
