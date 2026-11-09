package com.lms.live.service;

import com.lms.live.entity.LiveSession;
import com.lms.live.enums.LiveSessionStatus;
import com.lms.live.event.LiveSessionEndedEvent;
import com.lms.live.repository.LiveSessionRepository;
import io.livekit.server.RoomServiceClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-LIVE-09 — tự động kết thúc phiên Live khi giảng viên mất kết nối quá 60 giây.
 *
 * <p>{@code LiveKitWebhookController} ghi/xoá {@code instructorDisconnectedAt} theo thời gian
 * thực (sự kiện {@code participant_left}/{@code participant_joined}); job này chỉ quét định kỳ
 * để CHỐT hành động chuyển {@code ENDED} — tách 2 việc để không phụ thuộc đúng 1 webhook đến
 * đúng lúc (webhook có thể trễ/mất, quét định kỳ là lớp an toàn cuối).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionCronJob {

    private static final int GRACE_SECONDS = 60;

    private final LiveSessionRepository liveSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RoomServiceClient roomServiceClient;

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void endSessionsWithDisconnectedInstructor() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(GRACE_SECONDS);
        List<LiveSession> stale = liveSessionRepository
                .findByStatusAndInstructorDisconnectedAtIsNotNullAndInstructorDisconnectedAtLessThanEqual(
                        LiveSessionStatus.LIVE, threshold);
        if (stale.isEmpty()) {
            return;
        }

        for (LiveSession session : stale) {
            session.setStatus(LiveSessionStatus.ENDED);
            session.setEndedAt(LocalDateTime.now());
            log.info("BR-LIVE-09: tu dong ket thuc phien live {} do giang vien mat ket noi qua {}s",
                    session.getId(), GRACE_SECONDS);
        }
        liveSessionRepository.saveAll(stale);
        stale.forEach(session -> {
            eventPublisher.publishEvent(new LiveSessionEndedEvent(session.getId()));
            deleteRoomBestEffort(session.getRoomName());
        });
    }

    /** Xem javadoc {@link LiveSessionService#deleteRoomBestEffort} — cùng lý do: đóng phòng thật
     * phía LiveKit, không chỉ đổi trạng thái DB, tránh phát sinh phí từ kết nối "treo". */
    private void deleteRoomBestEffort(String roomName) {
        try {
            roomServiceClient.deleteRoom(roomName).execute();
        } catch (Exception e) {
            log.warn("Khong dong duoc phong LiveKit {} (khong anh huong trang thai DB): {}",
                    roomName, e.getMessage());
        }
    }
}
