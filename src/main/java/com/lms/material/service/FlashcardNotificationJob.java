package com.lms.material.service;

import com.lms.material.repository.projection.FlashcardDueCount;
import com.lms.material.repository.FlashcardReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lms.common.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardNotificationJob {

    private final FlashcardReviewRepository reviewRepository;
    private final NotificationService notificationService;

    private static final int MAX_PER_USER = 3;

    /**
     * Cronjob chạy lúc 8:00 AM mỗi ngày để gửi thông báo ôn tập Flashcard.
     * Thuật toán: Quét các thẻ có nextReviewAt <= ngày hiện tại.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void notifySpacedRepetition() {
        log.info("Starting SRS Notification Job...");
        LocalDateTime now = LocalDateTime.now();
        
        // Đếm thẻ đến hạn (hoặc quá hạn) theo từng học viên NGAY TRONG DB — trước đây nạp toàn bộ bảng
        // flashcard_reviews rồi lọc/gom nhóm bằng Java (hiệu năng đợt 3). Có sẵn index (user_id, next_review_at).
        List<FlashcardDueCount> dueByUser = reviewRepository.countDueByUser(now);

        // Vừa lưu DB (người offline xem lại ở chuông thông báo) vừa bắn WebSocket tới
        // /topic/notifications/{userId} (BR-NOTIFY-01). Trước đây chỉ bắn WebSocket tới topic theo EMAIL
        // trong khi FE nghe theo userId nên thông báo chưa từng tới người dùng.
        // Mỗi (học viên, khóa) một thông báo có tên khóa và đường dẫn tới đúng khóa đó; tối đa MAX_PER_USER khóa nhiều thẻ nhất
        // cho mỗi người mỗi ngày để người học nhiều khóa không bị dội thông báo.
        Map<Long, List<FlashcardDueCount>> byUser = dueByUser.stream()
                .collect(Collectors.groupingBy(FlashcardDueCount::userId, LinkedHashMap::new, Collectors.toList()));
        int notified = 0;
        for (Map.Entry<Long, List<FlashcardDueCount>> entry : byUser.entrySet()) {
            List<FlashcardDueCount> top = entry.getValue().stream()
                    .sorted(Comparator.comparing(FlashcardDueCount::count).reversed())
                    .limit(MAX_PER_USER)
                    .toList();
            for (FlashcardDueCount due : top) {
                try {
                    notificationService.notify(
                            due.userId(),
                            "SRS_REMINDER",
                            "Đến giờ ôn flashcard",
                            String.format("Khóa \"%s\": %d thẻ Flashcard cần ôn tập hôm nay để duy trì chuỗi nhớ!", due.courseTitle(), due.count()),
                            "/materials?courseId=" + due.courseId());
                    notified++;
                } catch (RuntimeException ex) {
                    // Một học viên lỗi không được làm hỏng nhắc nhở của những người còn lại.
                    log.warn("Không gửi được nhắc ôn flashcard cho user {}: {}", due.userId(), ex.getMessage());
                }
            }
        }

        log.info("SRS Notification Job completed. Sent {} reminders.", notified);
    }
}
