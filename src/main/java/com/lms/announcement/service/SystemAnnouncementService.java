package com.lms.announcement.service;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
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

    /** Số người nhận đọc mỗi lô khi phát tán thông báo hệ thống. */
    private static final int RECIPIENT_BATCH = 500;

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

    /** Danh sách cho admin: tìm theo tiêu đề/nội dung, phân trang (tối đa 50/trang). */
    @Transactional(readOnly = true)
    public List<Res> search(String query, int page, int size) {
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)));
        return announcementRepository.search(q, pageable).stream().map(this::toRes).toList();
    }

    /** Sửa tiêu đề/nội dung/hạn banner; cập nhật luôn các thông báo đã gửi tới người dùng. Thông báo đã thu hồi thì không sửa được. */
    @Transactional
    public Res update(Long id, com.lms.announcement.dto.SystemAnnouncementDto.UpdateReq req) {
        SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SystemAnnouncement", id));
        if (a.getRevokedAt() != null) {
            throw new InvalidRequestException("Thông báo này đã được thu hồi nên không thể chỉnh sửa.");
        }
        a.setTitle(req.title().trim());
        a.setContent(req.content().trim());
        a.setExpiresAt(req.expiresAt());
        SystemAnnouncement saved = announcementRepository.save(a);
        notificationService.updateBySource(sourceRef(id), saved.getTitle(), saved.getContent());
        return toRes(saved);
    }

    /** Thu hồi: ẩn khỏi mọi người dùng (kể cả banner), giữ bản ghi ở admin. Gọi lại khi đã thu hồi thì không làm gì (idempotent). */
    @Transactional
    public Res revoke(Long id) {
        SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SystemAnnouncement", id));
        if (a.getRevokedAt() == null) {
            a.setRevokedAt(LocalDateTime.now());
            announcementRepository.save(a);
            int hidden = notificationService.hideBySource(sourceRef(id));
            log.info("Da thu hoi thong bao he thong {} ({} thong bao nguoi dung bi an)", id, hidden);
        }
        return toRes(a);
    }

    /** Khôi phục thông báo đã thu hồi: hiện lại cho người dùng (banner còn hạn cũng hiện lại). Thông báo chưa bị thu hồi thì không làm gì (idempotent). */
    @Transactional
    public Res restore(Long id) {
        SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SystemAnnouncement", id));
        if (a.getRevokedAt() != null) {
            a.setRevokedAt(null);
            announcementRepository.save(a);
            int shown = notificationService.unhideBySource(sourceRef(id));
            log.info("Da khoi phuc thong bao he thong {} ({} thong bao nguoi dung hien lai)", id, shown);
        }
        return toRes(a);
    }

    /** Xóa hẳn một thông báo ĐÃ THU HỒI (bản ghi và các thông báo đã gửi). Thông báo còn hiệu lực phải thu hồi trước — tránh xóa nhầm. */
    @Transactional
    public void deleteRevoked(Long id) {
        SystemAnnouncement a = announcementRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SystemAnnouncement", id));
        if (a.getRevokedAt() == null) {
            throw new InvalidRequestException("Chỉ xóa được thông báo đã thu hồi. Hãy thu hồi thông báo trước.");
        }
        int removed = notificationService.deleteBySource(sourceRef(id));
        announcementRepository.delete(a);
        log.info("Da xoa thong bao he thong {} ({} thong bao nguoi dung bi xoa)", id, removed);
    }

    static String sourceRef(Long announcementId) {
        return "ANN:" + announcementId;
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
        if (announcement.getRevokedAt() != null) {
            log.info("Thong bao he thong {} da bi thu hoi truoc khi phat tan, bo qua", announcementId);
            return;
        }

        announcement.setDispatchStatus(AnnouncementDispatchStatus.PROCESSING);
        announcementRepository.save(announcement);

        String notifyType = "SYSTEM_" + announcement.getSeverity();
        int sent = 0;
        int total;
        if (announcement.getAudience() == AnnouncementAudience.SPECIFIC_USER) {
            List<Long> ids = userRepository.findById(announcement.getTargetUserId())
                    .map(u -> List.of(u.getId())).orElse(List.of());
            total = ids.size();
            sent += notifyAll(ids, notifyType, announcement, announcementId);
        } else {
            // Hiệu năng đợt 3: đọc ID người nhận theo lô RECIPIENT_BATCH — không nạp toàn bộ entity
            // người dùng vào bộ nhớ khi gửi cho "Tất cả" (trước đây userRepository.findAll()).
            Role role = switch (announcement.getAudience()) {
                case INSTRUCTOR -> Role.INSTRUCTOR;
                case STUDENT -> Role.STUDENT;
                default -> null;
            };
            total = (int) userRepository.countForAudience(role);
            int page = 0;
            Slice<Long> slice;
            do {
                slice = userRepository.findIdsForAudience(role, PageRequest.of(page++, RECIPIENT_BATCH));
                sent += notifyAll(slice.getContent(), notifyType, announcement, announcementId);
            } while (slice.hasNext());
        }
        announcement.setTotalRecipients(total);

        announcement.setSentCount(sent);
        announcement.setDispatchStatus(AnnouncementDispatchStatus.DONE);
        announcementRepository.save(announcement);
        log.info("Da phat tan xong thong bao he thong {} ({}/{} nguoi nhan)", announcementId, sent, total);
    }

    private int notifyAll(List<Long> userIds, String notifyType, SystemAnnouncement announcement, Long announcementId) {
        int sent = 0;
        for (Long userId : userIds) {
            try {
                notificationService.notify(userId, notifyType, announcement.getTitle(), announcement.getContent(), null, sourceRef(announcementId));
                sent++;
            } catch (Exception e) {
                log.error("Khong gui duoc thong bao he thong {} cho user {}", announcementId, userId, e);
            }
        }
        return sent;
    }

    private Res toRes(SystemAnnouncement a) {
        return new Res(
                a.getId(), a.getTitle(), a.getContent(), a.getSeverity(), a.getAudience(), a.getTargetUserId(),
                a.getExpiresAt(), a.getDispatchStatus(), a.getTotalRecipients(), a.getSentCount(),
                a.getCreatedByAdmin().getFullName(), a.getCreatedAt(), a.getRevokedAt()
        );
    }
}
