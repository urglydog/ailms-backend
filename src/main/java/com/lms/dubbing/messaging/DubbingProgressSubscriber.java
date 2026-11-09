package com.lms.dubbing.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * UC20 — nhận tiến độ lồng tiếng từ AI Worker qua Redis Pub/Sub (kênh
 * {@code lms:dubbing:progress}, xem {@code app/redis_client.py::publish_progress}) rồi forward
 * qua STOMP {@code /topic/dubbing/{lessonId}}. Payload đã là JSON hợp lệ do phía Python xây sẵn
 * (cả sự kiện cấp-chunk lẫn cấp-job — phân biệt bằng có/không có field {@code chunkIndex}), nên
 * ở đây chỉ cần đọc {@code lessonId} rồi forward nguyên văn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DubbingProgressSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode payload = objectMapper.readTree(body);
            long lessonId = payload.get("lessonId").asLong();
            log.debug("Forward tien do long tieng den /topic/dubbing/{}: {}", lessonId, body);
            messagingTemplate.convertAndSend("/topic/dubbing/" + lessonId, payload);
        } catch (Exception e) {
            // 1 message hong (JSON sai dinh dang, thieu lessonId...) khong duoc lam chet
            // listener - cac message sau van phai tiep tuc nhan duoc.
            log.error("Khong xu ly duoc payload tien do long tieng: {}", body, e);
        }
    }
}
