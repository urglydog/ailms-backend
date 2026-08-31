package com.lms.common.config;

import io.livekit.server.RoomServiceClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Giai đoạn 11 — thông tin kết nối LiveKit Cloud, dùng để sinh AccessToken (JWT) và xác thực webhook. */
@Configuration
@Getter
public class LiveKitConfig {

    /** wss://xxx.livekit.cloud — FE dùng giá trị này để connect LiveKit React SDK. */
    @Value("${lms.livekit.server-url}")
    private String serverUrl;

    @Value("${lms.livekit.api-key}")
    private String apiKey;

    @Value("${lms.livekit.api-secret}")
    private String apiSecret;

    /**
     * Client gọi LiveKit Server REST API (khác {@code AccessToken} chỉ SINH token) — dùng để CHỦ
     * ĐỘNG đóng hẳn phòng ({@code deleteRoom}) khi phiên live kết thúc, thay vì chỉ đổi trạng thái
     * DB rồi tin tưởng mọi trình duyệt tự ngắt kết nối. Quan trọng cho việc chống phát sinh phí
     * LiveKit oan nếu có trình duyệt "treo" không tự rời phòng (BR-LIVE-09/UC50).
     * {@code createClient} tự đổi {@code wss://} thành {@code https://} bên trong, không cần tự
     * xử lý scheme URL.
     */
    @Bean
    public RoomServiceClient roomServiceClient() {
        return RoomServiceClient.createClient(serverUrl, apiKey, apiSecret);
    }
}
