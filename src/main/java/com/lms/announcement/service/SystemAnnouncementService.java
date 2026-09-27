package com.lms.announcement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.announcement.dto.SystemAnnouncementDto.BannerRes;
import com.lms.announcement.dto.SystemAnnouncementDto.CreateReq;
import com.lms.announcement.dto.SystemAnnouncementDto.Res;
import com.lms.announcement.entity.SystemAnnouncement;
import com.lms.announcement.repository.SystemAnnouncementRepository;
import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.AnnouncementAudience;
import com.lms.common.enums.AnnouncementDispatchStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thông báo hệ thống từ Admin — phát tán (fan-out) tới toàn hệ thống/Giảng viên/Học viên/1 cá
 * nhân, xử lý NỀN qua hàng đợi Redis (26/09/2026, tính năng mới), CÙNG khuôn với
 * {@link com.lms.wishlist.service.WishlistPriceDropService}: {@code create()} chỉ lưu bản ghi +
 * đẩy job rồi trả về NGAY (Admin không phải chờ phát hết cho hàng nghìn user), việc gửi thật cho
 * từng người diễn ra ở {@link #consumeQueue()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAnnouncementService {

    private static final int MAX_JOBS_PER_TICK = 5;

    private final SystemAnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lms.redis-keys.admin-broadcast-queue:lms:admin-broadcast:jobs}")
    private String queueKey;

    public record BroadcastJob(Long announcementId) {}

    @Transactional
    public Res create(String adminEmail, CreateReq req) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminEmail));

        if (req.audience() == AnnouncementAudience.SPECIFIC_USER && req.targetUserId() == null) {
            throw new InvalidRequestException("Vui lòng chọn người nhận cụ thể (audience = SPECIFIC_USER).");
        }
        if (req.audience() != AnnouncementAudience.SPECIFIC_USER && req.targetUserId() != null) {
            throw new InvalidRequestException("targetUserId chỉ được set khi audience = SPECIFIC_USER.");
        }
        if (req.targetUserId() != null && !userRepository.existsById(req.targetUserId())) {
            throw new ResourceNotFoundException("User", req.targetUserId());
        }

        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setTitle(req.title().trim());
        announcement.setContent(req.content().trim());
        announcement.setSeverity(req.severity());
        announcement.setAudience(req.audience());
        announcement.setTargetUserId(req.targetUserId());
        announcement.setExpiresAt(req.expiresAt());
        announcement.setCreatedByAdmin(admin);
        announcement.setDispatchStatus(AnnouncementDispatchStatus.PENDING);
        SystemAnnouncement saved = announcementRepository.save(announcement);

        enqueueDispatch(saved.getId());
        return toRes(saved);
    }

    private void enqueueDispatch(Long announcementId) {
        try {
            redisTemplate.opsForList().leftPush(queueKey, objectMapper.writeValueAsString(new BroadcastJob(announcementId)));
        } catch (JsonProcessingException e) {
            log.error("Khong tao duoc job phat tan thong bao he thong {}", announcementId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<Res> listAll() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public BannerRes getActiveBanner() {
        return announcementRepository.findLatestActiveHighSeverity(LocalDateTime.now())
                .map(a -> new BannerRes(a.getId(), a.getTitle(), a.getContent(), a.getSeverity()))
                .orElse(null);
    }

    @Scheduled(fixedDelay = 3000)
    public void consumeQueue() {
        String payload;
        int processed = 0;
        while (processed < MAX_JOBS_PER_TICK && (payload = redisTemplate.opsForList().rightPop(queueKey)) != null) {
            try {
                BroadcastJob job = objectMapper.readValue(payload, BroadcastJob.class);
                dispatch(job.announcementId());
            } catch (Exception e) {
                log.error("Loi xu ly job phat tan thong bao he thong: {}", payload, e);
            }
            processed++;
        }
    }

    /**
     * KHÔNG đánh dấu {@code @Transactional} ở MỨC PHƯƠNG THỨC NÀY dù bên trong có nhiều thao tác
     * ghi — lý do GIỐNG {@code WishlistPriceDropService.processJob}: bị gọi self-invocation từ
     * {@link #consumeQueue()} trong cùng bean nên {@code @Transactional} ở đây sẽ bị bỏ qua âm
     * thầm. Mỗi bước (load/save qua repository, {@code notify()} từng người) đã tự có transaction
     * riêng — CỐ Ý không gộp cả lượt phát tán hàng nghìn người vào 1 transaction duy nhất (transaction
     * dài giữ connection/lock lâu là phản tác dụng cho đúng use case broadcast này).
     */
    void dispatch(Long announcementId) {
        SystemAnnouncement announcement = announcementRepository.findById(announcementId).orElse(null);
        if (announcement == null) {
            log.warn("Khong tim thay SystemAnnouncement {} de phat tan (co the da bi xoa)", announcementId);
            return;
        }

        announcement.setDispatchStatus(AnnouncementDispatchStatus.PROCESSING);
        announcementRepository.save(announcement);

        List<User> recipients = resolveRecipients(announcement);
        announcement.setTotalRecipients(recipients.size());

        String notifyType = "SYSTEM_" + announcement.getSeverity();
        int sent = 0;
        for (User user : recipients) {
            try {
                notificationService.notify(user.getId(), notifyType, announcement.getTitle(), announcement.getContent(), null);
                sent++;
            } catch (Exception e) {
                log.error("Khong gui duoc thong bao he thong {} cho user {}", announcementId, user.getId(), e);
            }
        }

        announcement.setSentCount(sent);
        announcement.setDispatchStatus(AnnouncementDispatchStatus.DONE);
        announcementRepository.save(announcement);
        log.info("Da phat tan xong thong bao he thong {} ({}/{} nguoi nhan)", announcementId, sent, recipients.size());
    }

    private List<User> resolveRecipients(SystemAnnouncement announcement) {
        return switch (announcement.getAudience()) {
            case ALL -> userRepository.findAll();
            case INSTRUCTOR -> userRepository.findByRole(Role.INSTRUCTOR);
            case STUDENT -> userRepository.findByRole(Role.STUDENT);
            case SPECIFIC_USER -> userRepository.findById(announcement.getTargetUserId()).map(List::of).orElse(List.of());
        };
    }

    private Res toRes(SystemAnnouncement a) {
        return new Res(
                a.getId(), a.getTitle(), a.getContent(), a.getSeverity(), a.getAudience(), a.getTargetUserId(),
                a.getExpiresAt(), a.getDispatchStatus(), a.getTotalRecipients(), a.getSentCount(),
                a.getCreatedByAdmin().getFullName(), a.getCreatedAt()
        );
    }
}
