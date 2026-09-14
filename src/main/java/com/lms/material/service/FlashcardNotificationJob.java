package com.lms.material.service;

import com.lms.auth.entity.User;
import com.lms.material.entity.FlashcardReview;
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
import java.util.stream.Collectors;

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
        
        // Tìm tất cả các review cần ôn tập hôm nay (hoặc đã quá hạn)
        List<FlashcardReview> dueReviews = reviewRepository.findAll().stream()
                .filter(r -> r.getNextReviewAt() != null && !r.getNextReviewAt().isAfter(now))
                .toList();

        // Nhóm theo người dùng
        Map<User, Long> userDueCount = dueReviews.stream()
                .collect(Collectors.groupingBy(FlashcardReview::getUser, Collectors.counting()));

        // Gửi qua WebSocket
        userDueCount.forEach((user, count) -> {
            String message = String.format("Bạn có %d thẻ Flashcard cần ôn tập hôm nay để duy trì chuỗi nhớ!", count);
            // Gửi tới topic của user
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + user.getEmail(),
                    Map.of(
                            "type", "SRS_REMINDER",
                            "message", message,
                            "dueCount", count,
                            "timestamp", System.currentTimeMillis()
                    )
            );
            log.debug("Sent SRS notification to {}: {}", user.getEmail(), message);
        });
        
        log.info("SRS Notification Job completed. Notified {} users.", userDueCount.size());
    }
}
