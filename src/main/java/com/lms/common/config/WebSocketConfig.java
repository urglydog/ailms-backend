package com.lms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
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
 * STOMP (cần cho kênh riêng tư {@code /topic/notifications/{userId}}) sẽ thêm bằng 1
 * {@code ChannelInterceptor} đọc JWT từ header của frame {@code CONNECT} khi hiện thực F5.3 —
 * chưa cần ở bước hạ tầng này vì kênh {@code /topic/dubbing/{lessonId}} không mang dữ liệu
 * riêng tư (chỉ tiến độ %, không phải nội dung bài học).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

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
