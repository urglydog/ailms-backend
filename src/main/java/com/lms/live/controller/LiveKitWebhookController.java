package com.lms.live.controller;

import com.lms.common.config.LiveKitConfig;
import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.repository.LiveSessionRepository;
import com.lms.live.service.LiveSessionService;
import io.livekit.server.WebhookReceiver;
import java.time.LocalDateTime;
import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR-LIVE-09 — LiveKit Cloud gọi vào đây khi participant tham gia/rời phòng. KHÔNG xác thực
 * bằng JWT (LiveKit không có access token của hệ thống) — {@code SecurityConfig} mở public
 * endpoint này, xác thực thật sự nằm ở {@link WebhookReceiver#receive} (kiểm chữ ký HMAC bằng
 * chính {@code apiSecret}, giống mô hình HMAC của IPN thanh toán {@code PaymentService}).
 *
 * <p>Chỉ quan tâm participant có identity dạng {@code instructor-<id>} (xem
 * {@link LiveSessionService#instructorIdentity}) — học viên rời/vào phòng không ảnh hưởng gì
 * tới BR-LIVE-09.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LiveKitWebhookController {

    private final LiveKitConfig liveKitConfig;
    private final LiveSessionRepository liveSessionRepository;

    @PostMapping("/api/v1/live/webhooks/livekit")
    public ResponseEntity<Void> handle(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        WebhookReceiver receiver = new WebhookReceiver(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
        WebhookEvent event;
        try {
            event = receiver.receive(body, authHeader);
        } catch (RuntimeException e) {
            log.warn("Webhook LiveKit khong hop le, bo qua: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        handleEvent(event);
        return ResponseEntity.ok().build();
    }

    @Transactional
    void handleEvent(WebhookEvent event) {
        String identity = event.getParticipant().getIdentity();
        if (identity.isEmpty() || !identity.startsWith("instructor-")) {
            return;
        }
        String roomName = event.getRoom().getName();
        if (roomName.isEmpty()) {
            return;
        }

        liveSessionRepository.findByRoomNameAndStatus(roomName, LiveSessionStatus.LIVE)
                .filter(session -> LiveSessionService.instructorIdentity(session.getInstructor().getId()).equals(identity))
                .ifPresent(session -> applyEvent(session, event.getEvent()));
    }

    private void applyEvent(LiveSession session, String eventType) {
        switch (eventType) {
            case "participant_left" -> {
                session.setInstructorDisconnectedAt(LocalDateTime.now());
                liveSessionRepository.save(session);
            }
            case "participant_joined" -> {
                session.setInstructorDisconnectedAt(null);
                liveSessionRepository.save(session);
            }
            default -> { /* room_started/room_finished/... không cần xử lý ở đây */ }
        }
    }
}
