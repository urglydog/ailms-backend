package com.lms.common.config;

import com.lms.dubbing.messaging.DubbingProgressSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * F5.3 — đăng ký lắng nghe kênh Redis Pub/Sub tiến độ lồng tiếng
 * ({@code lms.redis-keys.dubbing-progress}). {@link com.lms.dubbing.messaging.DubbingProgressSubscriber}
 * forward mỗi message nhận được qua STOMP.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final DubbingProgressSubscriber dubbingProgressSubscriber;

    @Value("${lms.redis-keys.dubbing-progress}")
    private String dubbingProgressChannel;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(dubbingProgressSubscriber, new ChannelTopic(dubbingProgressChannel));
        return container;
    }
}
