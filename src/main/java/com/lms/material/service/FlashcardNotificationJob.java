package com.lms.material.service;

import com.lms.material.repository.projection.FlashcardDueCount;
import com.lms.material.repository.FlashcardReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardNotificationJob {

    private final FlashcardReviewRepository reviewRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Cronjob chạy lúc 8:00 AM mỗi ngày để gửi thông báo ôn tập Flashcard.
     * Thuật toán: Quét các thẻ có nextReviewAt <= ngày hiện tại.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional(readOnly = true)
    public void notifySpacedRepetition() {
        log.info("Starting SRS Notification Job...");
        LocalDateTime now = LocalDateTime.now();
        
        // Đếm thẻ đến hạn (hoặc quá hạn) theo từng học viên NGAY TRONG DB — trước đây nạp toàn bộ bảng
        // flashcard_reviews rồi lọc/gom nhóm bằng Java (hiệu năng đợt 3). Có sẵn index (user_id, next_review_at).
        List<FlashcardDueCount> dueByUser = reviewRepository.countDueByUser(now);

        // Gửi qua WebSocket
        dueByUser.forEach(due -> {
            String email = due.email();
            long count = due.count();
            String message = String.format("Bạn có %d thẻ Flashcard cần ôn tập hôm nay để duy trì chuỗi nhớ!", count);
            // Gửi tới topic của user
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + email,
                    Map.of(
                            "type", "SRS_REMINDER",
                            "message", message,
                            "dueCount", count,
                            "timestamp", System.currentTimeMillis()
                    )
            );
            log.debug("Sent SRS notification to {}: {}", email, message);
        });
        
        log.info("SRS Notification Job completed. Notified {} users.", dueByUser.size());
    }
}
