package com.lms.wishlist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.entity.User;
import com.lms.auth.service.EmailService;
import com.lms.catalog.entity.Course;
import com.lms.common.service.NotificationService;
import com.lms.wishlist.entity.WishlistItem;
import com.lms.wishlist.repository.WishlistItemRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Báo giảm giá cho học viên đang wishlist 1 khóa học (26/09/2026, tính năng mới) — xử lý NỀN qua
 * hàng đợi Redis thay vì vòng lặp đồng bộ ngay trong request sửa khóa học của giảng viên (khóa có
 * hàng trăm người wishlist thì request đó sẽ chậm hẳn nếu gửi email đồng bộ).
 *
 * <p>Cùng khuôn producer (`leftPush` + JSON qua Jackson) với
 * {@link com.lms.catalog.service.CourseEmbeddingService}, nhưng ở đây Java backend VỪA đẩy VỪA tự
 * tiêu thụ hàng đợi của chính mình ({@link #consumeQueue()}, `rightPop`) — khác các hàng đợi
 * `lms:dubbing:jobs`/`lms:transcript:jobs`/`lms:course-embedding:jobs` vốn do Python AI worker
 * tiêu thụ. Đây là dạng job nền ĐẦU TIÊN trong dự án mà chính Spring Boot backend tự pop hàng đợi
 * Redis do chính nó đẩy vào — không có `@Async`/thread pool riêng, tái dùng
 * {@code @EnableScheduling} đã bật sẵn ({@code LmsApplication}), đúng tinh thần tối giản hạ tầng
 * đã dùng cho các job cron khác trong dự án (vd {@code FlashcardNotificationJob}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistPriceDropService {

    /** Trần số job xử lý mỗi lượt quét — tránh 1 lượt quét kẹt quá lâu nếu hàng đợi dồn ứ bất
     * thường; phần còn lại sẽ được xử lý ở lượt quét kế tiếp (5s sau). */
    private static final int MAX_JOBS_PER_TICK = 20;

    private final WishlistItemRepository wishlistItemRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.wishlist-price-drop-queue:lms:wishlist-price-drop:jobs}")
    private String queueKey;

    /** Job payload — {@code record} nên Jackson (de)serialize thẳng, không cần DTO riêng. */
    public record PriceDropJob(Long courseId, String courseTitle, String courseSlug, BigDecimal oldPrice, BigDecimal newPrice) {}

    /** Gọi ngay sau khi {@code CourseService.update()} phát hiện giá MỚI thấp hơn giá CŨ thật
     * sự — chỉ đẩy payload vào Redis rồi trả về ngay, không tự gửi thông báo/email tại đây. */
    public void enqueuePriceDrop(Course course, BigDecimal oldPrice, BigDecimal newPrice) {
        try {
            PriceDropJob job = new PriceDropJob(course.getId(), course.getTitle(), course.getSlug(), oldPrice, newPrice);
            redisTemplate.opsForList().leftPush(queueKey, objectMapper.writeValueAsString(job));
        } catch (JsonProcessingException e) {
            log.error("Khong tao duoc job bao giam gia wishlist cho course {}", course.getId(), e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void consumeQueue() {
        String payload;
        int processed = 0;
        while (processed < MAX_JOBS_PER_TICK && (payload = redisTemplate.opsForList().rightPop(queueKey)) != null) {
            try {
                processJob(objectMapper.readValue(payload, PriceDropJob.class));
            } catch (Exception e) {
                log.error("Loi xu ly job bao giam gia wishlist: {}", payload, e);
            }
            processed++;
        }
    }

    /**
     * KHÔNG đánh dấu {@code @Transactional}: phương thức này bị GỌI NỘI BỘ từ
     * {@link #consumeQueue()} trong CÙNG bean (self-invocation) — proxy AOP của Spring không chặn
     * được lời gọi kiểu này nên {@code @Transactional} đặt ở đây sẽ bị ÂM THẦM bỏ qua, không báo
     * lỗi gì cả (bẫy quen thuộc của Spring AOP). May mắn là không cần: {@code findByCourse_Id} đã
     * nạp sẵn {@code user} qua {@code @EntityGraph} (không lazy-load), còn {@code notify()}/
     * {@code save()} mỗi lời gọi đã tự có transaction riêng của nó rồi.
     */
    void processJob(PriceDropJob job) {
        List<WishlistItem> items = wishlistItemRepository.findByCourse_Id(job.courseId());
        String linkUrl = "/courses/" + job.courseSlug();
        String title = "🔥 Khóa học trong danh sách yêu thích đã giảm giá!";
        String content = "\"" + job.courseTitle() + "\" vừa giảm giá."
                + " Vào xem ngay trước khi hết ưu đãi nhé!";

        for (WishlistItem item : items) {
            User user = item.getUser();
            try {
                notificationService.notify(user.getId(), "WISHLIST_PRICE_DROP", title, content, linkUrl);
            } catch (Exception e) {
                log.error("Khong tao duoc thong bao trong app cho user {} (job giam gia course {})",
                        user.getId(), job.courseId(), e);
            }
            emailService.sendPriceDropEmail(user.getEmail(), job.courseTitle(), job.courseSlug(), job.oldPrice(), job.newPrice());

            // Chốt lại mốc giá MỚI cho người này — lần giảm giá TIẾP THEO so sánh với giá vừa báo,
            // không báo lại cùng 1 lần giảm nếu job này vô tình chạy trùng (idempotent theo ý nghĩa
            // nghiệp vụ, dù DB không có ràng buộc unique nào ép điều đó).
            item.setPriceAtAdd(job.newPrice());
            wishlistItemRepository.save(item);
        }

        log.info("Da xu ly xong job bao giam gia wishlist cho course {} ({} nguoi nhan)", job.courseId(), items.size());
    }
}
