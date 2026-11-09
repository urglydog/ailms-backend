package com.lms.enrollment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.common.exception.ConcurrentStreamException;
import com.lms.enrollment.dto.LessonPlayerDto.HeartbeatReq;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerHeartbeatService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String STREAM_PREFIX = "user_stream:";
    private static final Duration TTL = Duration.ofSeconds(30);

    public record StreamSession(
            String sessionId,
            String deviceName,
            long timestamp
    ) {}

    public void processHeartbeat(String userId, Long lessonId, HeartbeatReq req) {
        String key = STREAM_PREFIX + userId;
        
        if (req.force()) {
            // Cưỡng chế giành quyền phát
            saveSession(key, req);
            return;
        }

        String existingData = redisTemplate.opsForValue().get(key);
        if (existingData != null) {
            try {
                StreamSession currentSession = objectMapper.readValue(existingData, StreamSession.class);
                if (!Objects.equals(currentSession.sessionId(), req.sessionId())) {
                    // Trùng lặp stream
                    throw new ConcurrentStreamException("Video đã tạm dừng vì tài khoản của bạn đang phát video trên một thiết bị khác.");
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse stream session for user {}", userId, e);
            }
        }
        
        // Cập nhật TTL
        saveSession(key, req);
    }

    private void saveSession(String key, HeartbeatReq req) {
        StreamSession session = new StreamSession(req.sessionId(), req.deviceName(), System.currentTimeMillis());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(session), TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize stream session", e);
        }
    }
}
