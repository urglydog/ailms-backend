package com.lms.common.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
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
}
