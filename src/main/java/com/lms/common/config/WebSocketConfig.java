package com.lms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP qua SockJS — đẩy tiến độ lồng tiếng (UC20) và thông báo (BR-NOTIFY-01) realtime.
 *
 * <p>Endpoint bắt tay {@code /ws} đã được whitelist public ở {@link SecurityConfig}
 * ({@code PUBLIC_ENDPOINTS}) — SockJS cần 1 request HTTP thường trước khi nâng cấp lên
 * WebSocket nên không thể yêu cầu JWT ngay ở tầng đó. Xác thực người dùng cho từng kết nối
 * STOMP (cần cho kênh riêng tư {@code /topic/notifications/{userId}}) đã thêm bằng
 * {@code ChannelInterceptor} đọc JWT từ header của frame {@code CONNECT} ({@link StompAuthInterceptor}). Ghi chú cũ:
 * chưa cần ở bước hạ tầng này vì kênh {@code /topic/dubbing/{lessonId}} không mang dữ liệu
 * riêng tư (chỉ tiến độ %, không phải nội dung bài học).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Kiểm JWT lúc CONNECT và quyền SUBSCRIBE topic riêng tư (hiệu năng đợt 5). */
    private final StompAuthInterceptor stompAuthInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }

    @Value("${lms.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker đơn giản trong bộ nhớ — đủ cho quy mô KLTN, không cần RabbitMQ/ActiveMQ ngoài.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
